package com.farmerbb.taskbar.util

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.farmerbb.taskbar.R
import com.farmerbb.taskbar.activity.MainActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppEntryTest {
    private lateinit var context: Context
    private lateinit var appEntry: AppEntry
    private lateinit var componentName: ComponentName
    private lateinit var icon: Drawable

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        componentName = ComponentName(context, MainActivity::class.java)
        icon = context.resources.getDrawable(R.drawable.tb_apps)
        appEntry = AppEntry(
                context.packageName,
                componentName.flattenToString(),
                context.packageName,
                icon,
                true
        )
    }

    @Test
    fun testGetComponentName() {
        Assert.assertEquals(componentName.flattenToString(), appEntry.componentName)
    }

    @Test
    fun testGetPackageName() {
        Assert.assertEquals(context.packageName, appEntry.packageName)
    }

    @Test
    fun testGetLabel() {
        Assert.assertEquals(context.packageName, appEntry.label)
    }

    @Test
    fun testGetUserId() {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val currentUser = userManager.getSerialNumberForUser(Process.myUserHandle())
        Assert.assertEquals(currentUser, appEntry.getUserId(context))
        appEntry.setUserId(currentUser + 1)
        Assert.assertEquals(currentUser + 1, appEntry.getUserId(context))
        appEntry.setUserId(currentUser)
    }

    @Test
    fun testGetIcon() {
        Assert.assertEquals(icon, appEntry.getIcon(context))
    }

    @Test
    fun testSetLastTimeUsed() {
        Assert.assertEquals(0, appEntry.lastTimeUsed)
        appEntry.lastTimeUsed = 100
        Assert.assertEquals(100, appEntry.lastTimeUsed)
        appEntry.lastTimeUsed = 0
    }

    @Test
    fun testSetTotalTimeInForeground() {
        Assert.assertEquals(0, appEntry.totalTimeInForeground)
        appEntry.totalTimeInForeground = 100
        Assert.assertEquals(100, appEntry.totalTimeInForeground)
        appEntry.totalTimeInForeground = 0
    }

    @Test
    fun `test customText setter and getter`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = "AB"
        assertThat(entry.customText).isEqualTo("AB")
        assertThat(entry.hasCustomText()).isTrue()
    }

    @Test
    fun `test customText max length`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = "ABCDEFG"
        assertThat(entry.customText).isEqualTo("AB")
    }

    @Test
    fun `test customText empty and null`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        entry.customText = ""
        assertThat(entry.hasCustomText()).isFalse()

        entry.customText = null
        assertThat(entry.hasCustomText()).isFalse()
    }

    @Test
    fun `test customIconByteArray`() {
        val entry = AppEntry("com.test", "com.test.Main", "Test", null, false)

        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val drawable = BitmapDrawable(context.resources, bitmap)
        entry.setCustomIconFromDrawable(drawable)

        assertThat(entry.hasCustomIcon()).isTrue()
        assertThat(entry.customIconByteArray).isNotNull()
    }
}
