package com.ebicep.chatplus.features.chatwindows

import com.ebicep.chatplus.config.Config
import com.ebicep.chatplus.events.EventBus
import com.ebicep.chatplus.features.chattabs.CHAT_TAB_HEIGHT
import com.ebicep.chatplus.features.chattabs.CHAT_TAB_Y_OFFSET
import com.ebicep.chatplus.hud.*
import com.ebicep.chatplus.util.GraphicsUtil.createPose
import com.ebicep.chatplus.util.GraphicsUtil.guiForward
import net.minecraft.client.gui.GuiGraphics

object ChatWindowsManager {

    fun createDefaultWindow(): ChatWindow {
        return ChatWindow().also {
            it.tabSettings.hideTabs = true
        }
    }

    init {
        EventBus.register<ChatScreenMouseClickedEvent>({ 10000 }) {
            // check if mouse in inside widows starting from last
            val chatWindows = Config.values.chatWindows
            for (i in chatWindows.size - 1 downTo 0) {
                val chatWindow = chatWindows[i]
                if (chatWindow.generalSettings.disabled) {
                    continue
                }
                if (insideWindow(chatWindow, it.mouseX, it.mouseY)) {
                    selectWindow(chatWindow)
                    return@register
                }
            }
        }
        EventBus.register<ChatRenderPreLinesEvent> {
            val chatWindow = it.chatWindow
            if (chatWindow.generalSettings.disabled) {
                return@register
            }
            val outline = chatWindow.outlineSettings
            if (!ChatManager.isChatFocused() && !(!ChatManager.isChatFocused() && outline.showWhenChatNotOpen)) {
                return@register
            }
            if (!outline.enabled) {
                return@register
            }
            val selectedTab = chatWindow.tabSettings.selectedTab
            val renderer = chatWindow.renderer
            val guiGraphics = it.guiGraphics
            val poseStack = guiGraphics.pose()
            poseStack.createPose {
                poseStack.guiForward(if (ChatManager.globalSelectedTab == selectedTab) 70.0 else 50.0)
                val outlineBoxType = outline.outlineBoxType
                val outlineTabType = outline.outlineTabType
                outlineBoxType.render(outlineTabType, guiGraphics, chatWindow, selectedTab, renderer)
                if (!chatWindow.tabSettings.hideTabs) {
                    outlineTabType.render(outlineBoxType, guiGraphics, chatWindow, selectedTab, renderer)
                }
            }
        }
        EventBus.register<GetMaxYEvent> {
            if (it.chatWindow.tabSettings.hideTabs) {
                return@register
            }
            it.maxY -= CHAT_TAB_HEIGHT
        }
        EventBus.register<GetDefaultYEvent> {
            if (it.chatWindow.tabSettings.hideTabs) {
                return@register
            }
            it.y -= CHAT_TAB_HEIGHT
        }
    }

    private fun insideWindow(chatWindow: ChatWindow, x: Double, y: Double): Boolean {
        val renderer = chatWindow.renderer
        val startX = renderer.getUpdatedX()
        val endX = startX + renderer.getUpdatedWidthValue()
        val startY = renderer.getUpdatedY() - renderer.getTotalLineHeight()
        var endY = renderer.getUpdatedY()
        if (!chatWindow.tabSettings.hideTabs) {
            endY += CHAT_TAB_HEIGHT + CHAT_TAB_Y_OFFSET
        }
        return startX < x && x < endX && startY < y && y < endY
    }

    fun selectWindow(newWindow: ChatWindow) {
        val oldWindow = ChatManager.selectedWindow
        if (oldWindow == newWindow) {
            return
        }
        oldWindow.tabSettings.selectedTab.resetFilter()
        Config.values.chatWindows.remove(newWindow)
        Config.values.chatWindows.add(newWindow)
        EventBus.post(WindowSwitchEvent(oldWindow, newWindow))
    }

    fun renderAll(guiGraphics: GuiGraphics, i: Int, j: Int, k: Int) {
        Config.values.chatWindows.forEachIndexed { index, it ->
            if (it.generalSettings.disabled) {
                return@forEachIndexed
            }
            val poseStack = guiGraphics.pose()
            poseStack.createPose {
                poseStack.guiForward(amount = index.toDouble() * 100)
                it.renderer.render(it, guiGraphics, i, j, k)
            }
        }
    }

}

data class WindowSwitchEvent(
    val oldWindow: ChatWindow,
    val newWindow: ChatWindow
)