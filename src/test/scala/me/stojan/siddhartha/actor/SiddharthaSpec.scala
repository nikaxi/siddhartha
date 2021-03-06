/*
 * Copyright (c) 2015 Stojan Dimitrovski
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.stojan.siddhartha.actor

import java.util

import akka.actor.Props
import akka.testkit.{TestActorRef, TestProbe}
import me.stojan.siddhartha.keyspace.{Key, Keyspace}
import me.stojan.siddhartha.message._
import me.stojan.siddhartha.test.ActorSystemSpec
import me.stojan.siddhartha.util.Bytes

import scala.concurrent.duration._

class SiddharthaSpec extends ActorSystemSpec("SiddharthaSpec") {

  "Siddhartha" should "activate on a Child message" in {
    val sdhRef = system.actorOf(Props[Siddhartha])
    val askerRef = TestProbe()

    askerRef.send(sdhRef, Status("active"))

    askerRef.expectMsg(Some(false))

    sdhRef ! Child((Keyspace.min, Keyspace.max))

    askerRef.send(sdhRef, Status("active"))

    askerRef.expectMsg(Some(true))
  }

  it should "when active, respond on Join messages" in {
    val sdhRef = TestActorRef[Siddhartha]

    sdhRef ! Child((Keyspace.min, Keyspace.max))

    sdhRef ! Join()

    val halved = Keyspace.halve(Keyspace.min, Keyspace.max)

    expectMsg(Duration(50, MILLISECONDS), Child((halved._2, halved._3)))
  }

  it should "when not active, respond to AskToJoin messages" in {
    val sdhRef = TestActorRef[Siddhartha]

    sdhRef ! AskToJoin(testActor)

    expectMsg(Duration(50, MILLISECONDS), Join())
  }

  it should "when active, do not respond to AskToJoin messages" in {
    val sdhRef = TestActorRef[Siddhartha]

    sdhRef ! Child((Keyspace.min, Keyspace.max))

    sdhRef ! AskToJoin(testActor)

    expectNoMsg(Duration(100, MILLISECONDS))
  }

  it should "when active, forward DHT messages to parent" in {
    val sdhProbe = TestProbe()

    val childRef = system.actorOf(Props[Siddhartha])

    childRef ! AskToJoin(sdhProbe.ref)

    sdhProbe.expectMsg(Duration(50, MILLISECONDS), Join())

    sdhProbe.send(childRef, Child(( Key(Bytes(1, 0, 0)), Key(Bytes(1, 2, 3)) )) )

    childRef ! Get(Key(Bytes(0, 2, 0)))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Get(Key(Bytes(0, 2, 0))))
  }

  it should "when active, forward DHT messages to children" in {
    val sdhProbe = TestProbe()

    val childRef = system.actorOf(Props[Siddhartha])

    childRef ! AskToJoin(sdhProbe.ref)

    sdhProbe.expectMsg(Duration(50, MILLISECONDS), Join())

    sdhProbe.send(childRef, Child((Bytes(), Bytes(0, 200.toByte))))

    val subChildRef = TestProbe()

    subChildRef.send(childRef, Join())

    subChildRef.expectMsg(Duration(50, MILLISECONDS), Child((Bytes(0, 100.toByte), Bytes(0, 200.toByte))))

    childRef ! Get(Bytes(0, 100))

    sdhProbe.expectNoMsg(Duration(100, MILLISECONDS))
    subChildRef.expectMsg(Duration(50, MILLISECONDS), Get(Bytes(0, 100)))

    childRef ! Put(Bytes(0, 199.toByte), Some(Bytes(1, 2, 3)))

    sdhProbe.expectNoMsg(Duration(100, MILLISECONDS))
    subChildRef.expectMsg(Duration(50, MILLISECONDS), Put(Bytes(0, 199.toByte), Some(Bytes(1, 2, 3))))
  }

  it should "when active, store and retrieve values from its own keyspace" in {
    val sdhProbe = TestProbe()

    val childRef = system.actorOf(Props[Siddhartha])

    childRef ! AskToJoin(sdhProbe.ref)

    sdhProbe.expectMsg(Duration(50, MILLISECONDS), Join())

    sdhProbe.send(childRef, Child(( Key(Bytes(1, 2, 3)), Key(Bytes(4, 5, 6)) ), {
      val map = new util.TreeMap[Key, Bytes]()

      map.put(Key(Bytes(1, 2, 8)), Bytes(1, 2, 8))

      map
    }))

    sdhProbe.send(childRef, Get(Key(Bytes(1, 2, 8))))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Value(Key(Bytes(1, 2, 8)), Some(Bytes(1, 2, 8))))

    sdhProbe.send(childRef, Get(Key(Bytes(2, 0, 0))))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Value(Key(Bytes(2, 0, 0)), None))

    sdhProbe.send(childRef, Put(Key(Bytes(2, 0, 0)), Some(Bytes(4, 5, 6))))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Value(Key(Bytes(2, 0, 0)), Some(Bytes(4, 5, 6))))

    sdhProbe.send(childRef, Get(Key(Bytes(2, 0, 0))))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Value(Key(Bytes(2, 0, 0)), Some(Bytes(4, 5, 6))))

    sdhProbe.send(childRef, Put(Key(Bytes(2, 0, 0)), None))

    sdhProbe.send(childRef, Get(Key(Bytes(2, 0, 0))))

    sdhProbe.expectMsg(Duration(100, MILLISECONDS), Value(Key(Bytes(2, 0, 0)), None))
  }
}
