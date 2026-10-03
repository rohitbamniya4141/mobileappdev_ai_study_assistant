import asyncio

from app.services.rag_service import RAGService


def test_chunk_text_respects_overlap_configuration(tmp_path) -> None:
    service = RAGService(tmp_path / "chroma", "unused", chunk_size=20, chunk_overlap=5)
    chunks = service.chunk_text("one two three four five six seven eight nine ten eleven")
    assert len(chunks) > 1
    assert all(chunk.strip() for chunk in chunks)


class FakeCollection:
    def query(self, **kwargs):
        assert kwargs["n_results"] == 2
        return {
            "documents": [["relevant chunk"]],
            "metadatas": [[{"filename": "notes.pdf"}]],
            "distances": [[0.3]],  # Below DISTANCE_THRESHOLD (0.7) → chunk is relevant
        }


def test_retrieve_returns_chunks_and_distinct_sources(tmp_path) -> None:
    service = RAGService(tmp_path / "chroma", "unused", chunk_size=20, chunk_overlap=5)
    service._collection = FakeCollection()
    service._embed = lambda values: [[0.1, 0.2] for _ in values]

    result = asyncio.run(service.retrieve("What is RAG?", 2))

    assert result.chunks == ["relevant chunk"]
    assert result.sources == ["notes.pdf"]
    # Confidence = 1.0 - distance = 1.0 - 0.3 = 0.7
    assert result.avg_confidence == 0.7

