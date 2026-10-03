"""Main API router — all endpoints require a valid JWT except the root info endpoint.

User-isolation is enforced at every level:
  * Documents are filtered and mutated by the authenticated user's id.
  * Chat sessions and messages are owned by the authenticated user.
  * RAG retrieval passes user_id so only that user's vector chunks are searched.
"""

import logging
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status

from app.auth import get_current_user
from app.config import get_settings
from app.database import DatabaseUnavailableError, MongoDatabase, run_db
from app.dependencies import get_database, get_llm_service, get_rag_service
from app.schemas import (
    ChatMessage,
    ChatSession,
    ChatSessionCreate,
    Document,
    FAQItem,
    QueryRequest,
    QueryResponse,
    UserResponse,
)
from app.services.document_service import (
    InvalidDocumentError,
    extract_text_from_docx,
    extract_text_from_pdf,
)
from app.services.llm_service import LLMService, LLMUnavailableError
from app.services.rag_service import RAGService

logger = logging.getLogger(__name__)
router = APIRouter(tags=["Study assistant"])

DatabaseDep = Annotated[MongoDatabase, Depends(get_database)]
RAGDep = Annotated[RAGService, Depends(get_rag_service)]
LLMDep = Annotated[LLMService, Depends(get_llm_service)]
CurrentUser = Annotated[UserResponse, Depends(get_current_user)]


def unavailable(exc: Exception) -> HTTPException:
    logger.exception("Request failed", exc_info=exc)
    return HTTPException(status_code=503, detail="A required service is temporarily unavailable.")


async def read_upload(file: UploadFile, maximum: int) -> bytes:
    data = bytearray()
    while chunk := await file.read(1024 * 1024):
        data.extend(chunk)
        if len(data) > maximum:
            raise HTTPException(status_code=413, detail="The uploaded file exceeds the configured size limit.")
    return bytes(data)


# ---------------------------------------------------------------------------
# API info (public)
# ---------------------------------------------------------------------------

@router.get("/", summary="API information")
async def root() -> dict[str, str]:
    return {"message": "Student Study Assistant API"}


# ---------------------------------------------------------------------------
# Documents (protected — user-scoped)
# ---------------------------------------------------------------------------

@router.post(
    "/upload-document",
    response_model=Document,
    status_code=status.HTTP_201_CREATED,
    summary="Upload a PDF or DOCX document",
)
async def upload_document(
    file: Annotated[UploadFile, File(description="A text-based PDF or DOCX document")],
    db: DatabaseDep,
    rag: RAGDep,
    current_user: CurrentUser,
) -> Document:
    settings = get_settings()
    filename = file.filename or "document"
    lower = filename.lower()

    _PDF_MIME = {
        None,
        "application/pdf",
        "application/x-pdf",
        "application/acrobat",
        "application/octet-stream",   # generic MIME — filename extension is the authority
    }
    _DOCX_MIME = {
        None,
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "application/octet-stream",
        "application/zip",            # DOCX is a ZIP container
    }

    if lower.endswith(".pdf") and file.content_type in _PDF_MIME:
        file_type = "pdf"
    elif lower.endswith(".docx") and file.content_type in _DOCX_MIME:
        file_type = "docx"
    else:
        raise HTTPException(
            status_code=415,
            detail="Only PDF (.pdf) and Word (.docx) files are supported.",
        )

    content = await read_upload(file, settings.max_upload_size_bytes)
    try:
        if file_type == "pdf":
            text = extract_text_from_pdf(content)
            error_context = "PDF"
        else:
            text = extract_text_from_docx(content)
            error_context = "DOCX"

        chunks = rag.chunk_text(text)
        if not chunks:
            raise InvalidDocumentError(f"No usable text chunks were found in the {error_context}.")

        # Associate document with the authenticated user
        document = Document(
            user_id=current_user.id,
            filename=filename,
            file_type=file_type,
            chunk_count=len(chunks),
        )
        # Store vectors with user_id metadata for retrieval isolation
        await rag.add_document(document.id, document.filename, chunks, current_user.id)
        try:
            await run_db(db.database.documents.insert_one(document.model_dump()))
        except DatabaseUnavailableError:
            await rag.delete_document(document.id)
            raise
        return document

    except InvalidDocumentError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Document upload failed")
        raise HTTPException(status_code=500, detail="Document processing failed.") from exc
    finally:
        await file.close()


@router.get("/documents", response_model=list[Document], summary="List the current user's documents")
async def get_documents(db: DatabaseDep, current_user: CurrentUser) -> list[Document]:
    try:
        return await run_db(
            db.database.documents
            .find({"user_id": current_user.id}, {"_id": 0})
            .sort("upload_date", -1)
            .to_list(1000)
        )
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


@router.delete("/documents/{doc_id}", summary="Delete one of the current user's documents")
async def delete_document(doc_id: str, db: DatabaseDep, rag: RAGDep, current_user: CurrentUser) -> dict[str, str]:
    try:
        # Enforce ownership — user_id must match
        result = await run_db(
            db.database.documents.delete_one({"id": doc_id, "user_id": current_user.id})
        )
        if result.deleted_count == 0:
            raise HTTPException(status_code=404, detail="Document not found.")
        await rag.delete_document(doc_id)
        return {"message": "Document deleted successfully"}
    except HTTPException:
        raise
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


# ---------------------------------------------------------------------------
# Chat sessions (protected — user-scoped)
# ---------------------------------------------------------------------------

@router.get("/chat-sessions", response_model=list[ChatSession], summary="List the current user's chat sessions")
async def get_chat_sessions(db: DatabaseDep, current_user: CurrentUser) -> list[ChatSession]:
    try:
        return await run_db(
            db.database.chat_sessions
            .find({"user_id": current_user.id}, {"_id": 0})
            .sort("updated_at", -1)
            .to_list(100)
        )
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


@router.post(
    "/chat-sessions",
    response_model=ChatSession,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new chat session",
)
async def create_chat_session(
    data: ChatSessionCreate, db: DatabaseDep, current_user: CurrentUser
) -> ChatSession:
    try:
        session = ChatSession(user_id=current_user.id, title=data.title)
        await run_db(db.database.chat_sessions.insert_one(session.model_dump()))
        return session
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


@router.delete("/chat-sessions/{session_id}", summary="Delete a chat session and all its messages")
async def delete_chat_session(
    session_id: str, db: DatabaseDep, current_user: CurrentUser
) -> dict[str, str]:
    try:
        result = await run_db(
            db.database.chat_sessions.delete_one(
                {"id": session_id, "user_id": current_user.id}
            )
        )
        if result.deleted_count == 0:
            raise HTTPException(status_code=404, detail="Chat session not found.")
        # Cascade delete all messages in this session
        await run_db(
            db.database.chat_messages.delete_many(
                {"session_id": session_id, "user_id": current_user.id}
            )
        )
        return {"message": "Session deleted successfully"}
    except HTTPException:
        raise
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


# ---------------------------------------------------------------------------
# RAG query (protected — user-scoped vector retrieval)
# ---------------------------------------------------------------------------

@router.post("/query", response_model=QueryResponse, summary="Ask a question about the current user's documents")
async def query_documents(
    request: QueryRequest, db: DatabaseDep, rag: RAGDep, llm: LLMDep, current_user: CurrentUser
) -> QueryResponse:
    try:
        # Verify the session belongs to the current user
        session = await run_db(
            db.database.chat_sessions.find_one(
                {"id": request.session_id, "user_id": current_user.id}
            )
        )
        if not session:
            raise HTTPException(
                status_code=404,
                detail="Chat session not found. Create a session first.",
            )

        # Retrieve only from this user's document chunks
        retrieval = await rag.retrieve(request.query, get_settings().retrieval_count, current_user.id)
        context, sources = "\n\n".join(retrieval.chunks), retrieval.sources
        confidence_score: float | None = retrieval.avg_confidence if retrieval.chunks else None

        if not context:
            faqs = await run_db(db.database.faqs.find({}, {"_id": 0}).to_list(100))
            context = "\n\n".join(f"Q: {faq['question']}\nA: {faq['answer']}" for faq in faqs)
            sources = ["FAQ Database"] if context else []
            confidence_score = None

        if not context:
            response = "I cannot answer this because the information is not in the uploaded documents."
        else:
            response = await llm.answer(context, request.query)

        # Persist the exchange with user_id association
        messages = [
            ChatMessage(session_id=request.session_id, user_id=current_user.id, role="user", content=request.query),
            ChatMessage(session_id=request.session_id, user_id=current_user.id, role="assistant", content=response, sources=sources),
        ]
        await run_db(db.database.chat_messages.insert_many([m.model_dump() for m in messages]))

        # Update session's updated_at timestamp
        await run_db(
            db.database.chat_sessions.update_one(
                {"id": request.session_id},
                {"$set": {"updated_at": datetime.now(UTC)}},
            )
        )

        return QueryResponse(response=response, sources=sources, confidence_score=confidence_score)

    except LLMUnavailableError as exc:
        raise HTTPException(
            status_code=503,
            detail="The answer service is unavailable. Check the configured LLM provider and try again.",
        ) from exc
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Document query failed")
        raise HTTPException(status_code=500, detail="Unable to process the question.") from exc


# ---------------------------------------------------------------------------
# Chat history (protected — user-scoped)
# ---------------------------------------------------------------------------

@router.get(
    "/chat-history/{session_id}",
    response_model=list[ChatMessage],
    summary="Get messages for a session (user must own the session)",
)
async def get_chat_history(
    session_id: str, db: DatabaseDep, current_user: CurrentUser
) -> list[ChatMessage]:
    try:
        # Verify ownership before returning messages
        session = await run_db(
            db.database.chat_sessions.find_one(
                {"id": session_id, "user_id": current_user.id}
            )
        )
        if not session:
            raise HTTPException(status_code=404, detail="Chat session not found.")

        return await run_db(
            db.database.chat_messages
            .find({"session_id": session_id, "user_id": current_user.id}, {"_id": 0})
            .sort("timestamp", 1)
            .to_list(1000)
        )
    except HTTPException:
        raise
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


# ---------------------------------------------------------------------------
# FAQs (public — no ownership, global knowledge base)
# ---------------------------------------------------------------------------

@router.post("/faqs/seed", summary="Seed built-in study FAQs")
async def seed_faqs(db: DatabaseDep) -> dict[str, str]:
    faqs = [
        FAQItem(question="What is RAG?", answer="RAG retrieves relevant knowledge before an LLM writes its answer.", category="Technical"),
        FAQItem(question="How do I study effectively?", answer="Use active recall, spaced repetition, and regular breaks.", category="Study Tips"),
        FAQItem(question="What are embeddings?", answer="Embeddings are numerical vectors that represent semantic meaning.", category="Technical"),
    ]
    try:
        if await run_db(db.database.faqs.count_documents({})):
            return {"message": "FAQs already seeded"}
        await run_db(db.database.faqs.insert_many([faq.model_dump() for faq in faqs]))
        return {"message": f"Seeded {len(faqs)} FAQs successfully"}
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


@router.get("/faqs", response_model=list[FAQItem], summary="List FAQs")
async def get_faqs(db: DatabaseDep) -> list[FAQItem]:
    try:
        return await run_db(db.database.faqs.find({}, {"_id": 0}).to_list(1000))
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc


# ---------------------------------------------------------------------------
# Stats (protected — user-specific counts)
# ---------------------------------------------------------------------------

@router.get("/stats", summary="Get knowledge-base statistics for the current user")
async def get_stats(db: DatabaseDep, rag: RAGDep, current_user: CurrentUser) -> dict[str, int]:
    try:
        return {
            "documents": await run_db(
                db.database.documents.count_documents({"user_id": current_user.id})
            ),
            "chunks": await rag.count_for_user(current_user.id),
            "faqs": await run_db(db.database.faqs.count_documents({})),
        }
    except DatabaseUnavailableError as exc:
        raise unavailable(exc) from exc
    except Exception as exc:
        raise unavailable(exc) from exc
