import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorResponse } from '../models/auth.models';

/**
 * Extracts the backend's structured error envelope (Section 2.4 of the API
 * Contract: `{ status, message, data, errors[] }`) from an `HttpErrorResponse`,
 * falling back gracefully if the body doesn't match the expected shape
 * (e.g. a network failure, a gateway timeout, or a non-JSON 5xx page).
 */
export function parseApiError(error: HttpErrorResponse): ApiErrorResponse {
  const body = error.error;

  if (body && typeof body === 'object' && 'message' in body) {
    return {
      status: typeof body.status === 'number' ? body.status : error.status,
      message: body.message ?? 'Something went wrong. Please try again later.',
      data: null,
      errors: Array.isArray(body.errors) ? body.errors : undefined,
    };
  }

  return {
    status: error.status,
    message: 'Something went wrong. Please try again later.',
    data: null,
  };
}

/** Returns the first field-level validation message, if any, else `null`. */
export function firstFieldError(error: ApiErrorResponse, field?: string): string | null {
  if (!error.errors || error.errors.length === 0) return null;
  const match = field ? error.errors.find((e) => e.field === field) : error.errors[0];
  return match?.detail ?? null;
}
