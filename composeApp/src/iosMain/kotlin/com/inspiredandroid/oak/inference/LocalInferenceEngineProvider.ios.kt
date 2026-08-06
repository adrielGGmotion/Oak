package com.inspiredandroid.oak.inference

actual fun createLocalInferenceEngine(): LocalInferenceEngine? = IosLiteRTInferenceEngine()
