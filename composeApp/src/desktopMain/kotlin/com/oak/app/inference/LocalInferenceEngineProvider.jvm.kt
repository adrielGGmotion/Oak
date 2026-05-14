package com.oak.app.inference

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = LiteRTInferenceEngine()
