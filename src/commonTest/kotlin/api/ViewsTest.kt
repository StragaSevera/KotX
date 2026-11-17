package ru.ought.kotx.api

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe
import ru.ought.kotx.autorun
import ru.ought.kotx.computed
import ru.ought.kotx.observable
import ru.ought.kotx.observable.ObservableProperty

@Suppress("unused")
val ViewsTests by testSuite {
    test("property handles and delegates share tracked storage") {
        val source = observable("first")
        var writable by source
        val readable by source.asView()
        val values = mutableListOf<String>()
        autorun { values.add(readable) }.use {
            writable = "second"
            source.value = "third"
            values.shouldBe(listOf("first", "second", "third"))
        }
    }

    test("computed handles track direct value reads") {
        val source = observable(1)
        val doubled = computed { source.value * 2 }
        val values = mutableListOf<Int>()
        autorun { values.add(doubled.value) }.use {
            source.value = 2
            values.shouldBe(listOf(2, 4))
        }
    }

    test("property views conceal writing and reuse their wrapper") {
        val source = observable(1)
        val view = source.asView()
        (view is ObservableProperty<*>).shouldBe(false)
        (view === source.asView()).shouldBe(true)
        (view === view.asView()).shouldBe(true)
    }

}
