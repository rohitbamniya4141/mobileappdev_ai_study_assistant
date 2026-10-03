import asyncio
from dataclasses import dataclass, field
from pathlib import Path

import chromadb
from chromadb.config import Settings as ChromaSettings
from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sentence_transformers import SentenceTransformer


@dataclass
class RetrievalResult:
    chunks: list[str]
    sources: list[str]
    avg_confidence: float = field(default=0.0)
    """Mean retrieval confidence in [0, 1] where 1.0 = perfect semantic match."""


class RAGService:
    """Local embedding and persistent Chroma vector-store operations.

    Uses LangChain Document objects internally so that chunks carry structured
    metadata (filename, doc_id, chunk_index, user_id) through the full pipeline.

    User isolation is enforced via ChromaDB ``where`` filters on ``user_id``
    so that one user's vectors are never exposed to another user's queries.
    """

    DISTANCE_THRESHOLD = 0.7  # Cosine distance; below this is considered relevant

    def __init__(self, chroma_path: Path, model_name: str, chunk_size: int, chunk_overlap: int) -> None:
        self._chroma_path, self._model_name = chroma_path, model_name
        self._chunk_size, self._chunk_overlap = chunk_size, chunk_overlap
        self._client = self._collection = self._model = None

    def _get_collection(self):
        if self._collection is None:
            self._chroma_path.mkdir(parents=True, exist_ok=True)
            self._client = chromadb.PersistentClient(
                path=str(self._chroma_path),
                settings=ChromaSettings(anonymized_telemetry=False),
            )
            self._collection = self._client.get_or_create_collection(
                name="student_documents", metadata={"hnsw:space": "cosine"}
            )
        return self._collection

    def _get_model(self) -> SentenceTransformer:
        if self._model is None:
            self._model = SentenceTransformer(self._model_name)
        return self._model

    def chunk_text(self, text: str) -> list[str]:
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=self._chunk_size, chunk_overlap=self._chunk_overlap, length_function=len
        )
        return [chunk for chunk in splitter.split_text(text) if chunk.strip()]

    def _embed(self, texts: list[str]) -> list[list[float]]:
        return self._get_model().encode(texts, convert_to_numpy=True).tolist()

    def _build_langchain_docs(
        self, document_id: str, filename: str, chunks: list[str], user_id: str
    ) -> list[Document]:
        """Wrap raw text chunks into LangChain Document objects with structured metadata.

        ``user_id`` is included so ChromaDB ``where`` filters can isolate each
        user's document chunks during retrieval.
        """
        return [
            Document(
                page_content=chunk,
                metadata={
                    "filename": filename,
                    "doc_id": document_id,
                    "chunk_index": i,
                    "user_id": user_id,
                },
            )
            for i, chunk in enumerate(chunks)
        ]

    async def add_document(
        self, document_id: str, filename: str, chunks: list[str], user_id: str
    ) -> None:
        """Embed and store document chunks tagged with the owning user's id."""
        lc_docs = self._build_langchain_docs(document_id, filename, chunks, user_id)
        texts = [doc.page_content for doc in lc_docs]
        embeddings = await asyncio.to_thread(self._embed, texts)
        ids = [f"{document_id}_chunk_{doc.metadata['chunk_index']}" for doc in lc_docs]
        meta = [doc.metadata for doc in lc_docs]
        await asyncio.to_thread(
            self._get_collection().add,
            ids=ids,
            embeddings=embeddings,
            documents=texts,
            metadatas=meta,
        )

    async def retrieve(self, query: str, count: int, user_id: str) -> RetrievalResult:
        """Retrieve the most relevant chunks for *query*, restricted to *user_id*'s documents.

        This is the critical security boundary: no user can receive chunks
        that belong to another user's uploaded documents.
        """
        try:
            # First, count how many chunks this user has to avoid ChromaDB
            # raising an error when n_results > number of available items.
            user_items = await asyncio.to_thread(
                self._get_collection().get,
                where={"user_id": user_id},
                include=[],
            )
            available = len(user_items.get("ids") or [])
            if available == 0:
                return RetrievalResult(chunks=[], sources=[])

            actual_count = min(count, available)
            embedding = (await asyncio.to_thread(self._embed, [query]))[0]
            results = await asyncio.to_thread(
                self._get_collection().query,
                query_embeddings=[embedding],
                n_results=actual_count,
                where={"user_id": user_id},
            )

            all_chunks = (results.get("documents") or [[]])[0] or []
            all_metadata = (results.get("metadatas") or [[]])[0] or []
            all_distances = (results.get("distances") or [[]])[0] or []

            # Filter by confidence threshold; convert cosine distance → confidence score
            chunks, metadata, confidences = [], [], []
            for chunk, meta, dist in zip(all_chunks, all_metadata, all_distances, strict=False):
                if dist < self.DISTANCE_THRESHOLD:
                    chunks.append(chunk)
                    metadata.append(meta)
                    confidences.append(round(1.0 - dist, 4))

            avg_confidence = round(sum(confidences) / len(confidences), 4) if confidences else 0.0
            sources = sorted({item["filename"] for item in metadata if item and item.get("filename")})
            return RetrievalResult(chunks=chunks, sources=sources, avg_confidence=avg_confidence)

        except Exception:
            # Gracefully return empty rather than crashing the query endpoint
            return RetrievalResult(chunks=[], sources=[])

    async def delete_document(self, document_id: str) -> None:
        collection = self._get_collection()
        result = await asyncio.to_thread(collection.get, where={"doc_id": document_id})
        if result.get("ids"):
            await asyncio.to_thread(collection.delete, ids=result["ids"])

    async def count(self) -> int:
        return await asyncio.to_thread(self._get_collection().count)

    async def count_for_user(self, user_id: str) -> int:
        """Return the number of vector chunks belonging to *user_id*."""
        try:
            result = await asyncio.to_thread(
                self._get_collection().get,
                where={"user_id": user_id},
                include=[],
            )
            return len(result.get("ids") or [])
        except Exception:
            return 0

    async def check(self) -> bool:
        try:
            await self.count()
            return True
        except Exception:
            return False
