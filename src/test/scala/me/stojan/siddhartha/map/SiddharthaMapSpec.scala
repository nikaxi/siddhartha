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

package me.stojan.siddhartha.map

import akka.util.Timeout
import me.stojan.siddhartha.keyspace.{Key, Keyspace}
import me.stojan.siddhartha.system.Dharma
import me.stojan.siddhartha.test.UnitSpec
import me.stojan.siddhartha.util.Bytes
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class SiddharthaMapSpec extends UnitSpec with ScalaFutures with BeforeAndAfterEach {

  var dharma: Dharma = null

  implicit val timeout = Timeout(10 seconds)
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(20, Seconds), interval = Span(30, Millis))

  override def beforeEach(): Unit = {
    dharma = Dharma("SiddharthaMapSpec")
  }

  override def afterEach(): Unit = {
    dharma.shutdown()
    dharma = null
  }

  "SiddharthaMap" should "put a value in the DHT" in {
    val sdh = dharma.createSiddhartha((Keyspace.min, Keyspace.max))
    val map = SiddharthaMap(sdh)

    whenReady(map.put( Key(Bytes(0, 1, 2, 3)), Some(Bytes(0, 1, 2, 3)) )) { result =>
      result.value should be (Some(Bytes(0, 1, 2, 3)))
    }
  }

  it should "remove a value from the DHT" in {
    val sdh = dharma.createSiddhartha((Keyspace.min, Keyspace.max))
    val map = SiddharthaMap(sdh)

    whenReady(map.put( Key(Bytes(0, 1, 2, 3)), Some(Bytes(0, 1, 2, 3)) )) { result =>
      result.value should be (Some(Bytes(0, 1, 2, 3)))

      whenReady(map.remove(Key(Bytes(0, 1, 2, 3)))) { result =>
        result.value should be (None)
      }
    }
  }

  it should "get a value from the DHT" in {
    val sdh = dharma.createSiddhartha((Keyspace.min, Keyspace.max))
    val map = SiddharthaMap(sdh)

    whenReady(map.get(Key(Bytes(0, 1, 2, 3)))) { result =>
      result.value should be(None)

      whenReady(map.put( Key(Bytes(0, 1, 2, 3)), Some(Bytes(0, 1, 2, 3)) )) { result =>

        whenReady(map.get( Key(Bytes(0, 1, 2, 3)) )) { result =>
          result.value should be (Some(Bytes(0, 1, 2, 3)))

          whenReady(map.remove( Key(Bytes(0, 1, 2, 3)) )) { result =>

            whenReady(map.get( Key(Bytes(0, 1, 2, 3)) )) { result =>
              result.value should be (None)
            }
          }
        }
      }
    }
  }
}
