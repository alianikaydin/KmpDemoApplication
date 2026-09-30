package com.anksoft.myapplication.core.domain

/**
 * Marker for every error type that can travel through [Result].
 * Named [Error] deliberately so domain code reads `Result<User, DataError>`;
 * shadows nothing in commonMain since kotlin.Error is a Throwable subclass we never use.
 */
interface Error
