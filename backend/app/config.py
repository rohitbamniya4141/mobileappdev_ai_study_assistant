from functools import lru_cache
from pathlib import Path

from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

BACKEND_DIR = Path(__file__).resolve().parents[1]


class Settings(BaseSettings):
    """Runtime configuration loaded from the backend .env file or environment."""

    model_config = SettingsConfigDict(
        env_file=BACKEND_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
        enable_decoding=False,
    )
    app_name: str = "Student Study Assistant API"
    environment: str = "development"
    api_prefix: str = "/api"
    mongodb_url: str | None = Field(default=None, validation_alias="MONGO_URL")
    mongodb_database: str = Field(default="study_assistant", validation_alias="DB_NAME")
    llm_provider: str = "openai"
    openai_api_key: str | None = None
    llm_model: str = Field(
        default="gpt-4o-mini", validation_alias=AliasChoices("LLM_MODEL", "OPENAI_MODEL")
    )
    ollama_base_url: str = "http://127.0.0.1:11434/v1"
    chroma_path: Path = BACKEND_DIR / "chroma_data"
    embedding_model_name: str = "all-MiniLM-L6-v2"
    cors_origins: list[str] = ["http://localhost:3000", "http://127.0.0.1:3000"]
    max_upload_size_mb: int = Field(default=15, ge=1, le=100)
    chunk_size: int = Field(default=1000, ge=200, le=4000)
    chunk_overlap: int = Field(default=150, ge=0, le=1000)
    retrieval_count: int = Field(default=5, ge=1, le=10)

    # --- JWT Authentication ---
    jwt_secret_key: str = Field(
        default="change-this-in-production-use-a-long-random-string",
        validation_alias="JWT_SECRET_KEY",
    )
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = Field(default=10080, ge=1)  # 7 days

    @field_validator("cors_origins", mode="before")
    @classmethod
    def parse_cors_origins(cls, value: str | list[str]) -> list[str]:
        if isinstance(value, str):
            return [origin.strip().rstrip("/") for origin in value.split(",") if origin.strip()]
        return value

    @property
    def max_upload_size_bytes(self) -> int:
        return self.max_upload_size_mb * 1024 * 1024

    gemini_api_key: str | None = Field(default=None, validation_alias="GEMINI_API_KEY")
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta/openai/"

    @field_validator("llm_provider")
    @classmethod
    def validate_llm_provider(cls, value: str) -> str:
        provider = value.strip().lower()
        if provider not in {"openai", "ollama", "gemini"}:
            raise ValueError("LLM_PROVIDER must be 'openai', 'ollama', or 'gemini'.")
        return provider


@lru_cache
def get_settings() -> Settings:
    return Settings()
