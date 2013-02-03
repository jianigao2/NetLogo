// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.agent

import
  org.nlogo.api,
  org.nlogo.util.MersenneTwisterFast

// ArrayAgentSets are only used for agentsets which are never added to after they are initially
// created.  However note that turtles and links can die, so we may end up with an array containing
// some dead agents (agents with id -1).  There is some code below that attempts to replace dead
// agents with nulls (so the dead agents can be garbage collected), but that's not guaranteed to
// happen, so the contents of the array may be any mixture of live agents, dead agents, and nulls.
// - ST 7/24/07

object ArrayAgentSet {

  def apply(kind: api.AgentKind, world: World) =
    new ArrayAgentSet(kind, world, 0, null, 0, Array())

  def withCapacity(kind: api.AgentKind, world: World, initialCapacity: Int, printName: String = null) =
    new ArrayAgentSet(kind, world, initialCapacity, printName, 0,
      new Array[Agent](initialCapacity))

  // for use from Java since Java doesn't understand default args
  def withCapacity(kind: api.AgentKind, world: World, initialCapacity: Int) =
    new ArrayAgentSet(kind, world, initialCapacity, null, 0,
      new Array[Agent](initialCapacity))

  def fromAgent(agent: Agent) =
    fromArray(agent.kind, agent.world, Array(agent))

  def fromArray(kind: api.AgentKind, world: World, agents: Array[Agent], printName: String = null) =
    new ArrayAgentSet(kind, world, agents.size, printName, agents.size, agents)

  // for use from Java since Java doesn't understand default args
  def fromArray(kind: api.AgentKind, world: World, agents: Array[Agent]) =
    new ArrayAgentSet(kind, world, agents.size, null, agents.size, agents)
}

class ArrayAgentSet private (
  kind: api.AgentKind,
  world: World,
  initialCapacity: Int,
  printName: String,
  private var size: Int,
  private var array: Array[Agent])
extends AgentSet(kind, world, printName, false, false, false) {

  /// data

  private var capacity = initialCapacity

  /// conversions

  override def toArray = array

  override def toLogoList = {
    val result = collection.mutable.ArrayBuffer[Agent]()
    val iter = iterator
    while (iter.hasNext)
      result += iter.next()
    val resultArray = result.toArray
    java.util.Arrays.sort(resultArray.asInstanceOf[Array[AnyRef]])
    api.LogoList.fromIterator(resultArray.iterator)
  }

  override def toString = {
    val s = new StringBuilder("AgentSet")
    s.append("\n...... kind: ")
    s.append(kind.toString)
    s.append("\n...... size: " + size)
    s.append("\n...... count(): " + count)
    s.append("\n...... capacity: " + capacity)
    s.append("\n...... agents: ")
    val iter = iterator
    while (iter.hasNext)
      s.append("\n" + iter.next())
    s.toString
  }

  /// size

  override def isEmpty =
    if (kind == api.AgentKind.Turtle || kind == api.AgentKind.Link)
      // some of the turtles might be dead, so we need to actually scan - ST 2/27/03
      !iterator.hasNext
    else
      size == 0

  override def count =
    if (kind == api.AgentKind.Turtle || kind == api.AgentKind.Link) {
      // some of the turtles might be dead, so we need to actually count - ST 2/27/03
      var result = 0
      val iter = iterator
      while(iter.hasNext) {
        iter.next()
        result += 1
      }
      result
    }
    else size

  /// equality

  // assumes we've already checked for equal counts - ST 7/6/06
  override def equalAgentSetsHelper(otherSet: api.AgentSet) = {
    val set = collection.mutable.HashSet[api.Agent]()
    val iter = iterator
    while (iter.hasNext)
      set += iter.next()
    import collection.JavaConverters._
    otherSet.agents.asScala.forall(set.contains)
  }

  /// one-agent queries

  override def agent(l: Long): Agent = {
    val i = l.toInt
    if (kind == api.AgentKind.Turtle || kind == api.AgentKind.Link) {
      val agent = array(i)
      if (agent.id == -1) {
        array(i) = null
        null
      }
      else agent
    }
    else array(i)
  }

  override def getAgent(id: AnyRef) =
    array(id.asInstanceOf[java.lang.Double].intValue)

  override def contains(agent: api.Agent): Boolean = {
    val iter = iterator
    while (iter.hasNext)
      if (iter.next() eq agent)
        return true
    false
  }

  /// mutation

  override def add(agent: Agent) = {
    if (size >= capacity) {
      val newCapacity = capacity * 2 + 1
      val newArray = new Array[Agent](newCapacity)
      System.arraycopy(array, 0, newArray, 0, capacity)
      array = newArray
      capacity = newCapacity
    }
    array(size) = agent
    size += 1
  }

  override def remove(id: AnyRef): Unit =
    throw new UnsupportedOperationException

  override def clear(): Unit =
    throw new UnsupportedOperationException

  /// random selection

  // the next few methods take precomputedCount as an argument since we want to avoid _randomoneof
  // and _randomnof resulting in more than one total call to count(), since count() can be O(n)
  // - ST 2/27/03

  // assume agentset is nonempty, since _randomoneof.java checks for that
  override def randomOne(precomputedCount: Int, random: Int) =
    if (size == capacity && kind != api.AgentKind.Turtle && kind != api.AgentKind.Link)
      array(random)
    else {
      val iter = iterator
      var i = 0
      while (i < random) {
        iter.next()
        i += 1
      }
      iter.next()
    }

  // This is used to optimize the special case of randomSubset where size == 2
  override def randomTwo(precomputedCount: Int, ran1: Int, ran2: Int): Array[Agent] = {
    // we know precomputedCount, or this method would not have been called.
    // see randomSubset().
    val (random1, random2) =
      if (ran2 >= ran1)
        // if random2 >= random1, we need to increment random2 to choose a later agent.
        (ran1, ran2 + 1)
      else
        (ran2, ran1)
    if (size == capacity && kind != api.AgentKind.Turtle && kind != api.AgentKind.Link)
      Array(
        array(random1),
        array(random2))
    else {
      val it = iterator
      var i = 0
      // skip to the first random place
      while(i < random1) {
        it.next()
        i += 1
      }
      Array(it.next(), {
        // skip to the second random place
        i += 1
        while (i < random2) {
          it.next()
          i += 1
        }
        it.next()
      })
    }
  }

  override def randomSubsetGeneral(resultSize: Int, precomputedCount: Int, random: MersenneTwisterFast) = {
    val result = new Array[Agent](resultSize)
    if (precomputedCount == capacity) {
      var i, j = 0
      while (j < resultSize) {
        if (random.nextInt(precomputedCount - i) < resultSize - j) {
          result(j) = array(i)
          j += 1
        }
        i += 1
      }
    }
    else {
      val iter = iterator
      var i, j = 0
      while (j < resultSize) {
        val next = iter.next()
        if (random.nextInt(precomputedCount - i) < resultSize - j) {
          result(j) = next
          j += 1
        }
        i += 1
      }
    }
    result
  }

  /// iterator methods

  // returns an Iterator object of the appropriate class
  override def iterator: AgentIterator =
    if (kind != api.AgentKind.Turtle && kind != api.AgentKind.Link)
      new Iterator
    else
      new IteratorWithDead

  // shuffling iterator = shufflerator! (Google hits: 0)
  // Update: Now 5 Google hits, the first 4 of which are NetLogo related,
  // and the last one is a person named "SHUFFLER, Ator", which Google thought
  // was close enough!  ;-)  ~Forrest (10/3/2008)

  override def shufflerator(rng: MersenneTwisterFast): AgentIterator =
    // note it at the moment (and this should probably be fixed)
    // Job.runExclusive() counts on this making a copy of the
    // contents of the agentset - ST 12/15/05
    new Shufflerator(rng)

  /// iterator implementations

  private class Iterator extends AgentIterator {
    protected var index = 0
    override def hasNext = index < size
    override def next() = {
      val result = array(index)
      index += 1
      result
    }
  }

  // extended to skip dead agents
  private class IteratorWithDead extends Iterator {
    // skip initial dead agents
    while (index < size && array(index).id == -1)
      index += 1
    override def next() = {
      var resultIndex = index
      // skip to next live agent
      do index += 1
      while (index < size && array(index).id == -1)
      array(resultIndex)
    }
  }

  private class Shufflerator(rng: MersenneTwisterFast) extends Iterator {
    private[this] var i = 0
    private[this] val copy = new Array[Agent](size)
    private[this] var nextOne: Agent = null
    System.arraycopy(array, 0, copy, 0, size)
    while (i < copy.length && copy(i) == null)
      i += 1
    fetch()
    override def hasNext =
      nextOne != null
    override def next(): Agent = {
      val result = nextOne
      fetch()
      result
    }
    private def fetch() {
      if (i >= copy.length)
        nextOne = null
      else {
        if (i < copy.length - 1) {
          val r = i + rng.nextInt(copy.length - i)
          nextOne = copy(r)
          copy(r) = copy(i)
        }
        else
          nextOne = copy(i)
        i += 1
        // we could have a bunch of different Shufflerator subclasses
        // the same way we have Iterator subclasses in order to avoid
        // having to do both checks, but I'm not
        // sure it's really worth the effort - ST 3/15/06
        if (nextOne == null || nextOne.id == -1)
          fetch();
      }
    }
  }

}
