"""Authentication utilities: password hashing, JWT creation/validation, and the
``get_current_user`` FastAPI dependency used by all protected endpoints."""

import logging
from typing import Annotated
from datetime import UTC, datetime, timedelta

import bcrypt
import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import get_settings
from app.database import MongoDatabase, run_db
from app.dependencies import get_database
from app.schemas import UserResponse

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Password hashing  (direct bcrypt — avoids passlib/bcrypt4 incompatibility)
# ---------------------------------------------------------------------------


def hash_password(plain: str) -> str:
    """Return a bcrypt hash of *plain*.  The salt is generated automatically."""
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    """Return True if *plain* matches the stored bcrypt *hashed* string."""
    return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))


# ---------------------------------------------------------------------------
# JWT
# ---------------------------------------------------------------------------

def create_access_token(user_id: str) -> str:
    """Create a signed JWT access token with the user's id as the subject."""
    settings = get_settings()
    expire = datetime.now(UTC) + timedelta(minutes=settings.jwt_expire_minutes)
    payload = {"sub": user_id, "exp": expire, "iat": datetime.now(UTC)}
    return jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)


# ---------------------------------------------------------------------------
# Dependency
# ---------------------------------------------------------------------------

_bearer = HTTPBearer(auto_error=False)


async def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)],
    db: Annotated[MongoDatabase, Depends(get_database)],
) -> UserResponse:
    """FastAPI dependency that validates the Bearer token and returns the
    authenticated user.  Raises HTTP 401 on any authentication failure so that
    callers never need to handle the None case."""

    _unauth = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired authentication token.",
        headers={"WWW-Authenticate": "Bearer"},
    )

    if not credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required. Please log in.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    settings = get_settings()
    try:
        payload = jwt.decode(
            credentials.credentials,
            settings.jwt_secret_key,
            algorithms=[settings.jwt_algorithm],
        )
        user_id: str | None = payload.get("sub")
        if not user_id:
            raise jwt.InvalidTokenError("Missing subject claim")
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Your session has expired. Please log in again.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError:
        raise _unauth

    user_data = await run_db(
        db.database.users.find_one({"id": user_id}, {"_id": 0, "password_hash": 0})
    )
    if not user_data:
        logger.warning("Token valid but user %s not found in database", user_id)
        raise _unauth

    return UserResponse(**user_data)
