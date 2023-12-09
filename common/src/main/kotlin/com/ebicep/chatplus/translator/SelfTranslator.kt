package com.ebicep.chatplus.translator

import com.ebicep.chatplus.hud.ChatManager
import com.ebicep.chatplus.hud.ChatPlusScreen
import com.ebicep.chatplus.hud.ChatPlusScreen.Companion.splitChatMessage
import net.minecraft.client.Minecraft

class SelfTranslator(val toTranslate: String, val prefix: String) : Thread() {

    override fun run() {
        languageSpeak?.let {
            val translator = Translator(toTranslate, languageSelf, it)
            val translateResult = translator.translate(toTranslate) ?: return
            val messages = splitChatMessage(translateResult.translatedText)

            val translatedMessage = messages[0]
            ChatManager.addSentMessage(translatedMessage)
            if (prefix.isEmpty()) {
                Minecraft.getInstance().player!!.connection.sendChat(translatedMessage)
            } else {
                Minecraft.getInstance().player!!.connection.sendChat("$prefix $translatedMessage")
            }
            ChatPlusScreen.messagesToSend.addAll(messages.subList(1, messages.size))
        }
    }

}