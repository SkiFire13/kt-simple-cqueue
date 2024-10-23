package com.github.skifire13.simple_cqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class ConcurrentQueue<T> {
    class Node<T> internal constructor(var elem: T?) {
        internal val next: AtomicRef<Node<T>?> = atomic(null)
    }

    // Head of the queue. This will be updated atomically by the (concurrent) consumers.
    // This always lags behind the actual head by one, meaning the actual head is head.next.
    // This node is also the only one that can have elem == null.
    private val head: AtomicRef<Node<T>>
    // "Tail" of the queue. This will be updated atomically by the (concurrent) producers.
    // This is always guaranteed to be _some_ node of the queue, but not necessarily the last one.
    private val tail: AtomicRef<Node<T>>

    init {
        // Initially both head and tail point to the same node with no element.
        val node = Node<T>(null)
        head = atomic(node)
        tail = atomic(node)
    }

    fun push(elem: T) {
        // Prepare the new node to push.
        val node = Node(elem)

        // Load the tail node for the first time.
        // This will later be updated in the loop if the push fails.
        var tail = this.tail.value

        // Repeat until the push succeeds.
        while (true) {
            // Try atomically adding node to the end of the list.
            if (tail.next.compareAndSet(null, node)) {
                // If it succeeds update the value of the tail node and return.
                // Note that in the meantime some other producer may have pushed a new value,
                // so this update is not guaranteed to set the latest node, but it is one node of the list
                // because the above CAS succeeded.
                this.tail.value = node
                return
            } else {
                // If it fails load the next element of the list and repeat.
                // This is guaranteed to be not null because the `compareAndSet` just failed,
                // meaning `tail.next` was not null at that time, and we never overwrite non-null
                // `next` references with null ones.
                tail = tail.next.value!!
            }
        }
    }

    fun pop(): T? {
        // Load the head node for the first time.
        // This will later be updated in the loop if the pop fails.
        var head = this.head.value

        while (true) {
            // `this.head` always lags behind by one element, the actual head is pointed by its `next` field.
            // If that's null there's no node in the queue, hence no element to pop, and we return null.
            val next = head.next.value ?: return null

            // If `next` is not null then we can pop it.
            if (this.head.compareAndSet(head, next)) {
                // This is guaranteed to not be null because only `this.head` has `elem` = null,
                // and since we're the ones that popped it no other thread can set it to null.
                val elem = next.elem!!
                // Replace `next.elem` with null since it is not the head.
                // This will avoid leaking it if the queue is not popped anymore for some time.
                next.elem = null
                return elem
            } else {
                // Otherwise update `head` and retry
                head = next
            }
        }
    }
}
