package com.ebicep.chatplus.hud

import com.ebicep.chatplus.config.Config
import com.ebicep.chatplus.config.TimestampMode
import com.ebicep.chatplus.events.Events
import com.google.common.collect.Lists
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.ChatFormatting
import net.minecraft.client.ComponentCollector
import net.minecraft.client.GuiMessage
import net.minecraft.client.GuiMessageTag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.chat.ChatListener
import net.minecraft.locale.Language
import net.minecraft.network.chat.*
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.min


@Serializable
class ChatTab {

    data class ChatPlusGuiMessageLine(val line: GuiMessage.Line, val content: String)


    var name: String
    var pattern: String
        set(value) {
            field = value
            regex = Regex(value)
        }

    constructor(name: String, pattern: String) {
        this.name = name
        this.pattern = pattern
        this.regex = Regex(pattern)
    }

    @Transient
    var regex: Regex = Regex("")

    @Transient
    val messages: MutableList<GuiMessage> = ArrayList()

    @Transient
    val displayedMessages: MutableList<ChatPlusGuiMessageLine> = ArrayList()

    @Transient
    var chatScrollbarPos: Int = 0

    @Transient
    var updateChat = false

    @Transient
    var resetDisplayMessageAtTick = -1L

    fun addMessage(
        pChatComponent: Component,
        pHeaderSignature: MessageSignature?,
        pAddedTime: Int,
        pTag: GuiMessageTag?,
        pOnlyTrim: Boolean
    ) {
        if (!regex.matches(pChatComponent.string)) {
            return
        }
        val i = Mth.floor(ChatManager.getBackgroundWidth())
//            if (pTag?.icon() != null) {
//                i -= pTag.icon()!!.width + 4 + 2
//            }
        if (Config.values.chatTimestampMode != TimestampMode.NONE && !pOnlyTrim) {
            addTimestampToComponent(pChatComponent)
        }
        val list = wrapComponents(pChatComponent, i, Minecraft.getInstance().font)
        val flag = ChatManager.isChatFocused()
        for (j in list.indices) {
            val chatPlusLine = list[j]
            val formattedCharSequence = chatPlusLine.first
            val content = chatPlusLine.second
            if (flag && chatScrollbarPos > 0) {
                updateChat = true
                scrollChat(1)
            }
            val lastComponent = j == list.size - 1
            this.displayedMessages.add(
                ChatPlusGuiMessageLine(
                    GuiMessage.Line(pAddedTime, formattedCharSequence, pTag, lastComponent),
                    content
                )
            )
        }
        while (this.displayedMessages.size > Config.values.maxMessages.value) {
            this.displayedMessages.removeAt(0)
        }
        if (!pOnlyTrim) {
            this.messages.add(GuiMessage(pAddedTime, pChatComponent, pHeaderSignature, pTag))
            while (this.messages.size > Config.values.maxMessages.value) {
                this.messages.removeAt(0)
            }
        }
    }

    /**
     * Adds timestamp to bottom of chat message, works for most chat formats
     */
    private fun addTimestampToComponent(pChatComponent: Component) {
        val previousHover = pChatComponent.style.hoverEvent
        if (previousHover != null) {
            when (previousHover.action) {
                HoverEvent.Action.SHOW_TEXT -> {
                    val component: Component = previousHover.getValue(HoverEvent.Action.SHOW_TEXT)!!
                    component.siblings.add(getTimestamp(true))
                }
            }
        } else {
            pChatComponent.style.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, getTimestamp(false))
            // only add if parent does not have a hover event
            pChatComponent.siblings.forEach {
                addTimestampToComponent(it)
            }

//            if (pChatComponent.contents is TranslatableContents) {
//                pChatComponent.contents.visit(FormattedText.StyledContentConsumer<MutableComponent> { style : Style, contents: String ->
//                    style.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, getTimestamp(false))
//                    Optional.empty()
//                }, Style.EMPTY)
//            }
        }
    }

    private fun getTimestamp(newLine: Boolean): Component {
        return Component.literal((if (newLine) "\n" else "") + "Sent at ")
            .withStyle {
                it.withColor(ChatFormatting.GRAY)
            }
            .append(Component.literal(getCurrentTime())
                .withStyle {
                    it.withColor(ChatFormatting.YELLOW)
                })
            .append(Component.literal("."))
    }

    private fun getCurrentTime(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(Config.values.chatTimestampMode.format))
    }

    fun getMessageTagAt(pMouseX: Double, pMouseY: Double): GuiMessageTag? {
        val d0: Double = this.screenToChatX(pMouseX)
        val d1: Double = this.screenToChatY(pMouseY)
        val i: Int = this.getMessageEndIndexAt(d0, d1)
        if (i >= 0 && i < this.displayedMessages.size) {
            val guiMessageLine: GuiMessage.Line = this.displayedMessages[i].line
            val guiMessageTag = guiMessageLine.tag()
            if (guiMessageTag != null && this.hasSelectedMessageTag(d0, guiMessageLine, guiMessageTag)) {
                return guiMessageTag
            }
        }
        return null
    }

    fun getMessageAt(pMouseX: Double, pMouseY: Double): ChatPlusGuiMessageLine? {
        val x = screenToChatX(pMouseX)
        val y = screenToChatY(pMouseY)
        val i = getMessageLineIndexAt(x, y)
        val size = this.displayedMessages.size
        return if (i in 0 until size) {
            return this.displayedMessages[size - i - 1]
        } else {
            null
        }
    }

    private fun screenToChatX(pX: Double): Double {
        return (pX - ChatManager.getX()) / ChatManager.getScale()
    }

    private fun screenToChatY(pY: Double): Double {
        val d0: Double = ChatManager.getY() - pY
        return d0 / (ChatManager.getScale() * ChatManager.getLineHeight().toDouble())
    }

    private fun getMessageEndIndexAt(pMouseX: Double, pMouseY: Double): Int {
        var i: Int = this.getMessageLineIndexAt(pMouseX, pMouseY)
        return if (i == -1) {
            -1
        } else {
            while (i >= 0) {
                if (this.displayedMessages[i].line.endOfEntry()) {
                    return i
                }
                --i
            }
            i
        }
    }

    private fun getMessageLineIndexAt(pMouseX: Double, pMouseY: Double): Int {
        return if (ChatManager.isChatFocused() && !Minecraft.getInstance().options.hideGui) {
            if (pMouseX >= 0 && pMouseX <= Mth.floor(ChatManager.getBackgroundWidth())) {
                val i = min(ChatManager.getLinesPerPageScaled(), this.displayedMessages.size)
                if (pMouseY >= 0.0 && pMouseY < i.toDouble()) {
                    val j = Mth.floor(pMouseY + chatScrollbarPos.toDouble())
                    if (j >= 0 && j < this.displayedMessages.size) {
                        return j
                    }
                }
                -1
            } else {
                -1
            }
        } else {
            -1
        }
    }

    private fun hasSelectedMessageTag(p_240619_: Double, pLine: GuiMessage.Line, pTag: GuiMessageTag): Boolean {
        return if (p_240619_ < 0.0) {
            true
        } else {
            val guiMessageTagIcon = pTag.icon()
            if (guiMessageTagIcon == null) {
                false
            } else {
                val i: Int = this.getTagIconLeft(pLine)
                val j = i + guiMessageTagIcon.width
                p_240619_ >= i.toDouble() && p_240619_ <= j.toDouble()
            }
        }
    }

    fun getTagIconLeft(pLine: GuiMessage.Line): Int {
        return Minecraft.getInstance().font.width(pLine.content()) + 4
    }

    fun handleChatQueueClicked(pMouseX: Double, pMouseY: Double): Boolean {
        return if (ChatManager.isChatFocused() && !Minecraft.getInstance().options.hideGui) {
            val chatListener: ChatListener = Minecraft.getInstance().chatListener
            if (chatListener.queueSize() == 0L) {
                false
            } else {
                val d0 = pMouseX - 2.0
                val d1: Double = ChatManager.getY() - pMouseY
                if (d0 <= Mth.floor(ChatManager.getWidth().toDouble() / ChatManager.getScale())
                        .toDouble() && d1 < 0.0 && d1 > Mth.floor(-9.0 * ChatManager.getScale())
                        .toDouble()
                ) {
                    chatListener.acceptNextDelayedMessage()
                    true
                } else {
                    false
                }
            }
        } else {
            false
        }
    }

    fun drawTagIcon(pGuiGraphics: GuiGraphics, pLeft: Int, pBottom: Int, pTagIcon: GuiMessageTag.Icon) {
        val i = pBottom - pTagIcon.height - 1
        pTagIcon.draw(pGuiGraphics, pLeft, i)
    }

    fun resetChatScroll() {
        chatScrollbarPos = 0
        this.updateChat = false
    }

    fun scrollChat(pPosInc: Int) {
        chatScrollbarPos += pPosInc
        val displayedMessagesSize: Int = this.displayedMessages.size
        val maxScroll = displayedMessagesSize - ChatManager.getLinesPerPageScaled()
        if (chatScrollbarPos > maxScroll) {
            chatScrollbarPos = maxScroll
        }
        if (chatScrollbarPos <= 0) {
            chatScrollbarPos = 0
            this.updateChat = false
        }
    }

    fun rescaleChat() {
        resetChatScroll()
        queueRefreshDisplayedMessages()
    }

    private fun queueRefreshDisplayedMessages() {
        resetDisplayMessageAtTick = Events.currentTick + 2
    }

    fun refreshDisplayedMessage() {
        resetDisplayMessageAtTick = -1
        displayedMessages.clear()
        for (i in messages.indices) {
            val guiMessage: GuiMessage = messages[i]
            addMessage(guiMessage.content(), guiMessage.signature(), guiMessage.addedTime(), guiMessage.tag(), true)
        }
    }

    fun getClickedComponentStyleAt(pMouseX: Double, pMouseY: Double): Style? {
        val x = screenToChatX(pMouseX)
        val y = screenToChatY(pMouseY)
        val i = getMessageLineIndexAt(x, y)
        val size = this.displayedMessages.size
        return if (i in 0 until size) {
            val guiMessageLine: GuiMessage.Line = this.displayedMessages[size - i - 1].line
            Minecraft.getInstance().font.splitter.componentStyleAtWidth(guiMessageLine.content(), Mth.floor(x))
        } else {
            null
        }
    }

    fun render(guiGraphics: GuiGraphics) {
        val mc = Minecraft.getInstance()
        val poseStack = guiGraphics.pose()
        val isSelected = this == ChatManager.selectedTab
        val backgroundOpacity = ((if (isSelected) 255 else 100) * ChatManager.getBackgroundOpacity()).toInt() shl 24
        val textColor = if (isSelected) 0xffffff else 0x999999

        poseStack.pushPose()
        poseStack.translate(0.0f, 0.0f, 50.0f)
        guiGraphics.fill(0, 0, mc.font.width(name) + PADDING + PADDING, 9 + PADDING + PADDING, backgroundOpacity)
        poseStack.translate(0.0f, 0.0f, 50.0f)
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            name,
            PADDING,
            PADDING + PADDING / 2,
            textColor
        )
        poseStack.popPose()
    }


    companion object {
        val PADDING = 2

        private val INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY)

        private fun stripColor(pText: String): String? {
            return if (Minecraft.getInstance().options.chatColors().get()) pText else ChatFormatting.stripFormatting(pText)
        }

        fun wrapComponents(pComponent: FormattedText, pMaxWidth: Int, pFont: Font): List<Pair<FormattedCharSequence, String>> {
            val componentCollector = ComponentCollector()
            pComponent.visit({ style: Style, string: String ->
                componentCollector.append(FormattedText.of(stripColor(string)!!, style))
                Optional.empty<Any?>()
            }, Style.EMPTY)
            val list: MutableList<Pair<FormattedCharSequence, String>> = Lists.newArrayList()
            pFont.splitter.splitLines(
                componentCollector.resultOrEmpty, pMaxWidth, Style.EMPTY
            ) { formattedText: FormattedText, p_94004_: Boolean ->
                val formattedCharSequence = Language.getInstance().getVisualOrder(formattedText)
                list.add(
                    Pair(
                        if (p_94004_) FormattedCharSequence.composite(INDENT, formattedCharSequence) else formattedCharSequence,
                        formattedText.string
                    )
                )
            }
            return (if (list.isEmpty()) mutableListOf(Pair(FormattedCharSequence.EMPTY, "")) else list)
        }
    }

}