from datetime import UTC, datetime
from typing import Literal
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, EmailStr, Field


# ---------------------------------------------------------------------------
# User models
# ---------------------------------------------------------------------------

class UserCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=128)


class UserInDB(BaseModel):
    """Full user record as stored in MongoDB. Never returned to the client."""
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid4()))
    name: str
    email: str
    password_hash: str
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class UserResponse(BaseModel):
    """Safe user representation returned in API responses (no password hash)."""
    model_config = ConfigDict(extra="ignore")
    id: str
    name: str
    email: str
    created_at: datetime


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse


# ---------------------------------------------------------------------------
# Chat session models
# ---------------------------------------------------------------------------

class ChatSession(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid4()))
    user_id: str
    title: str = "New Conversation"
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class ChatSessionCreate(BaseModel):
    title: str = Field(default="New Conversation", min_length=1, max_length=200)


# ---------------------------------------------------------------------------
# Document models  (user_id added for ownership)
# ---------------------------------------------------------------------------

class Document(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid4()))
    user_id: str
    filename: str
    file_type: Literal["pdf", "docx"] = "pdf"
    chunk_count: int = Field(ge=1)
    upload_date: datetime = Field(default_factory=lambda: datetime.now(UTC))


# ---------------------------------------------------------------------------
# Chat message models  (user_id added for ownership)
# ---------------------------------------------------------------------------

class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid4()))
    session_id: str
    user_id: str
    role: Literal["user", "assistant"]
    content: str
    sources: list[str] | None = None
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))


# ---------------------------------------------------------------------------
# Query models
# ---------------------------------------------------------------------------

class QueryRequest(BaseModel):
    query: str = Field(min_length=1, max_length=4000, description="Question to answer from uploaded material.")
    session_id: str = Field(min_length=1, max_length=128)


class QueryResponse(BaseModel):
    response: str
    sources: list[str]
    confidence_score: float | None = Field(
        default=None,
        ge=0.0,
        le=1.0,
        description="Average semantic retrieval confidence (0=no match, 1=perfect). "
                    "None when the FAQ fallback or short-circuit path was taken.",
    )


# ---------------------------------------------------------------------------
# FAQ models
# ---------------------------------------------------------------------------

class FAQItem(BaseModel):
    model_config = ConfigDict(extra="ignore")
    id: str = Field(default_factory=lambda: str(uuid4()))
    question: str = Field(min_length=1, max_length=1000)
    answer: str = Field(min_length=1, max_length=4000)
    category: str = Field(min_length=1, max_length=100)


# ---------------------------------------------------------------------------
# Health
# ---------------------------------------------------------------------------

class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    dependencies: dict[str, str]
