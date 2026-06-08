package com.aichat.workbench.agent.skill

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.domain.model.SkillId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAssetSkillRegistryTest {
    @Test
    fun loadsBuiltInSkillFilesFromAssets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val registry = AndroidAssetSkillRegistry(context)

        val skills = registry.listSkills()

        assertEquals(listOf("code-task", "web-research", "image-generation"), skills.map { it.id.value })
        assertTrue(requireNotNull(registry.getSkill(SkillId("code-task"))).prompt.contains("Inspect relevant files"))
        assertTrue(requireNotNull(registry.getSkill(SkillId("web-research"))).prompt.contains("Search before answering"))
        assertTrue(requireNotNull(registry.getSkill(SkillId("image-generation"))).prompt.contains("image generation"))
    }
}
