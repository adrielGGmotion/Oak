package com.oak.app.network

import com.oak.app.inference.InferenceTimeoutException
import com.oak.app.inference.InsufficientMemoryException
import com.oak.app.inference.NoModelDownloadedException
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.error_context_window_exceeded
import oak.composeapp.generated.resources.error_empty_response
import oak.composeapp.generated.resources.error_file_too_large
import oak.composeapp.generated.resources.error_request_too_large
import oak.composeapp.generated.resources.error_insufficient_credits
import oak.composeapp.generated.resources.error_invalid_api_key
import oak.composeapp.generated.resources.error_openai_compatible_connection
import oak.composeapp.generated.resources.error_openai_compatible_model_not_found
import oak.composeapp.generated.resources.error_quota_exhausted
import oak.composeapp.generated.resources.error_rate_limit_exceeded
import oak.composeapp.generated.resources.error_unknown
import oak.composeapp.generated.resources.error_unsupported_file_type
import oak.composeapp.generated.resources.litert_error_inference_timeout
import oak.composeapp.generated.resources.litert_error_insufficient_memory
import oak.composeapp.generated.resources.litert_error_no_model
import org.jetbrains.compose.resources.StringResource

sealed class ApiException(message: String?, cause: Throwable? = null) : Exception(message, cause)

class GenericNetworkException(message: String, cause: Throwable? = null) : ApiException(message, cause)

sealed class GeminiApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class GeminiGenericException(message: String, cause: Throwable? = null) : GeminiApiException(message, cause)
class GeminiRateLimitExceededException : GeminiApiException()
class GeminiInvalidApiKeyException : GeminiApiException()

sealed class AnthropicApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class AnthropicGenericException(message: String, cause: Throwable? = null) : AnthropicApiException(message, cause)
class AnthropicInvalidApiKeyException : AnthropicApiException()
class AnthropicRateLimitExceededException : AnthropicApiException()
class AnthropicOverloadedException : AnthropicApiException()
class AnthropicInsufficientCreditsException : AnthropicApiException()

sealed class OpenAICompatibleApiException(message: String? = null, cause: Throwable? = null) : ApiException(message, cause)
class OpenAICompatibleGenericException(message: String, cause: Throwable? = null) : OpenAICompatibleApiException(message, cause)
class OpenAICompatibleInvalidApiKeyException : OpenAICompatibleApiException()
class OpenAICompatibleRateLimitExceededException : OpenAICompatibleApiException()
class OpenAICompatibleQuotaExhaustedException : OpenAICompatibleApiException()
class OpenAICompatibleConnectionException : OpenAICompatibleApiException()
class OpenAICompatibleModelNotFoundException : OpenAICompatibleApiException()
class OpenAICompatibleEmptyResponseException : OpenAICompatibleApiException()
class OpenAICompatibleRequestTooLargeException : OpenAICompatibleApiException()

class ContextWindowExceededException : ApiException(null)
class UnsupportedFileTypeException : ApiException(null)
class FileTooLargeException : ApiException(null)

sealed interface UiError {
    data class Resource(val resource: StringResource) : UiError
    data class Text(val message: String) : UiError
}

fun Exception.toUiError(): UiError = when (this) {
    is UnsupportedFileTypeException -> UiError.Resource(Res.string.error_unsupported_file_type)
    is FileTooLargeException -> UiError.Resource(Res.string.error_file_too_large)
    is ContextWindowExceededException -> UiError.Resource(Res.string.error_context_window_exceeded)
    is OpenAICompatibleRequestTooLargeException -> UiError.Resource(Res.string.error_request_too_large)
    is GeminiInvalidApiKeyException, is OpenAICompatibleInvalidApiKeyException, is AnthropicInvalidApiKeyException -> UiError.Resource(Res.string.error_invalid_api_key)
    is GeminiRateLimitExceededException, is OpenAICompatibleRateLimitExceededException, is AnthropicRateLimitExceededException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicOverloadedException -> UiError.Resource(Res.string.error_rate_limit_exceeded)
    is AnthropicInsufficientCreditsException -> UiError.Resource(Res.string.error_insufficient_credits)
    is OpenAICompatibleQuotaExhaustedException -> UiError.Resource(Res.string.error_quota_exhausted)
    is OpenAICompatibleConnectionException -> UiError.Resource(Res.string.error_openai_compatible_connection)
    is OpenAICompatibleModelNotFoundException -> UiError.Resource(Res.string.error_openai_compatible_model_not_found)
    is OpenAICompatibleEmptyResponseException -> UiError.Resource(Res.string.error_empty_response)
    is InsufficientMemoryException -> UiError.Resource(Res.string.litert_error_insufficient_memory)
    is InferenceTimeoutException -> UiError.Resource(Res.string.litert_error_inference_timeout)
    is NoModelDownloadedException -> UiError.Resource(Res.string.litert_error_no_model)
    is GeminiGenericException, is OpenAICompatibleGenericException, is AnthropicGenericException, is GenericNetworkException -> UiError.Text(message ?: "An unexpected error occurred.")
    else -> if (!message.isNullOrBlank()) UiError.Text(message!!) else UiError.Resource(Res.string.error_unknown)
}
