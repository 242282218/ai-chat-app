package com.aichat.workbench

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesMainNavigation() {
        composeRule.onNodeWithText("对话").assertIsDisplayed()
        composeRule.onNodeWithText("图片").assertIsDisplayed()
        composeRule.onNodeWithText("模型").assertIsDisplayed()
    }

    @Test
    fun providerSettingsBottomTabDoesNotShowBackButton() {
        composeRule.onNodeWithText("模型").performClick()

        composeRule.onNodeWithText("模型连接").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("返回").assertCountEquals(0)
    }
}
