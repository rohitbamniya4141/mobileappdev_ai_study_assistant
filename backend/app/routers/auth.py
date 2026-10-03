"""Authentication endpoints: register, login, and get the current user."""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status

from app.auth import create_access_token, get_current_user, hash_password, verify_password
from app.database import DatabaseUnavailableError, MongoDatabase, run_db
from app.dependencies import get_database
from app.schemas import LoginRequest, TokenResponse, UserCreate, UserInDB, UserResponse

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/auth", tags=["Authentication"])

DatabaseDep = Annotated[MongoDatabase, Depends(get_database)]
CurrentUser = Annotated[UserResponse, Depends(get_current_user)]


@router.post(
    "/register",
    response_model=TokenResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Create a new user account",
)
async def register(data: UserCreate, db: DatabaseDep) -> TokenResponse:
    """Register a new user.  Returns an access token on success so the client
    can proceed directly without a separate login step."""
    try:
        # Enforce email uniqueness
        existing = await run_db(
            db.database.users.find_one({"email": data.email.lower().strip()})
        )
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="An account with this email address already exists.",
            )

        user = UserInDB(
            name=data.name.strip(),
            email=data.email.lower().strip(),
            password_hash=hash_password(data.password),
        )
        await run_db(db.database.users.insert_one(user.model_dump()))

        token = create_access_token(user.id)
        user_response = UserResponse(
            id=user.id, name=user.name, email=user.email, created_at=user.created_at
        )
        logger.info("New user registered: %s", user.email)
        return TokenResponse(access_token=token, user=user_response)

    except HTTPException:
        raise
    except DatabaseUnavailableError as exc:
        logger.exception("Database error during registration")
        raise HTTPException(status_code=503, detail="Service temporarily unavailable.") from exc
    except Exception as exc:
        logger.exception("Unexpected error during registration")
        raise HTTPException(status_code=500, detail="Registration failed.") from exc


@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Log in with email and password",
)
async def login(data: LoginRequest, db: DatabaseDep) -> TokenResponse:
    """Authenticate with email + password.  Returns a JWT access token."""
    try:
        user_data = await run_db(
            db.database.users.find_one(
                {"email": data.email.lower().strip()}, {"_id": 0}
            )
        )
        # Use a single error message to prevent email enumeration
        if not user_data or not verify_password(data.password, user_data["password_hash"]):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email address or password.",
            )

        user = UserInDB(**user_data)
        token = create_access_token(user.id)
        user_response = UserResponse(
            id=user.id, name=user.name, email=user.email, created_at=user.created_at
        )
        logger.info("User logged in: %s", user.email)
        return TokenResponse(access_token=token, user=user_response)

    except HTTPException:
        raise
    except DatabaseUnavailableError as exc:
        raise HTTPException(status_code=503, detail="Service temporarily unavailable.") from exc
    except Exception as exc:
        logger.exception("Unexpected error during login")
        raise HTTPException(status_code=500, detail="Login failed.") from exc


@router.get(
    "/me",
    response_model=UserResponse,
    summary="Get the current authenticated user",
)
async def get_me(current_user: CurrentUser) -> UserResponse:
    """Validate the token and return the authenticated user's profile.
    The Android app calls this on startup to verify a stored token is still valid."""
    return current_user
