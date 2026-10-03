import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.database import MongoDatabase
from app.routers.api import router as api_router
from app.routers.auth import router as auth_router
from app.schemas import HealthResponse
from app.services.llm_service import LLMService
from app.services.rag_service import RAGService

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    app.state.database = MongoDatabase(settings)
    app.state.rag = RAGService(
        settings.chroma_path,
        settings.embedding_model_name,
        settings.chunk_size,
        settings.chunk_overlap,
    )
    app.state.llm = LLMService(
        settings.llm_provider,
        settings.openai_api_key,
        settings.llm_model,
        settings.ollama_base_url,
        gemini_api_key=settings.gemini_api_key,
    )
    yield
    app.state.database.close()


settings = get_settings()
app = FastAPI(
    title=settings.app_name,
    version="2.0.0",
    description="Secure RAG API with JWT authentication and per-user document isolation.",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE"],
    allow_headers=["Content-Type", "Authorization"],
)
app.include_router(auth_router, prefix=settings.api_prefix)
app.include_router(api_router, prefix=settings.api_prefix)


@app.get(
    "/health",
    response_model=HealthResponse,
    tags=["Operations"],
    summary="Check application and dependency health",
)
async def health() -> HealthResponse:
    mongodb = "available" if await app.state.database.ping() else "unavailable"
    chromadb = "available" if await app.state.rag.check() else "unavailable"
    overall = "ok" if mongodb == chromadb == "available" else "degraded"
    return HealthResponse(
        status=overall,
        dependencies={
            "mongodb": mongodb,
            "chromadb": chromadb,
            "llm": "configured" if app.state.llm.configured else "not_configured",
        },
    )
