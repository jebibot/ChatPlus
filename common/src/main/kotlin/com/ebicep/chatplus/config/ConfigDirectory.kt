package com.ebicep.chatplus.config

import dev.architectury.injectables.annotations.ExpectPlatform
import java.nio.file.Path

object ConfigDirectory {

    @JvmStatic
    @ExpectPlatform
    fun getConfigDirectory(): Path {
        throw AssertionError()
    }
}