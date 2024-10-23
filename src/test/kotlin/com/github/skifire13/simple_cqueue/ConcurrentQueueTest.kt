package com.github.skifire13.simple_cqueue

import org.jetbrains.kotlinx.lincheck.*
import org.jetbrains.kotlinx.lincheck.annotations.*
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.*
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions
import kotlin.test.Test

class ConcurrentQueueTest {
    private val queue = ConcurrentQueue<Int>()

    @Operation
    fun push(e: Int) = queue.push(e)

    @Operation(nonParallelGroup = "consumer")
    fun pop() = queue.pop()

    @Operation(nonParallelGroup = "consumer")
    fun pushVisible() {
        queue.push(0)
        // After a push we are guaranteed to see at least one element in the queue.
        // i.e. it is never the case that a push from some other thread is preventing
        // a push that has already returned from being visible.
        assert(queue.pop() != null);
    }

    @Test
    fun stressTest() = StressOptions().check(this::class)

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .checkObstructionFreedom()
        .check(this::class)
}
