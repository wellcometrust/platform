package uk.ac.wellcome.platform.matcher.workgraph

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.matcher.models.{
  WorkGraph,
  WorkNode,
  WorkUpdate
}

class LinkedWorkGraphUpdaterTest extends FunSpec with Matchers {

  // An existing graph of works is updated by changing the links of a single work.
  // The change may result in new compound works which are identified by the LinkedWorkGraphUpdater
  // works are shown by id with directed links, A->B means work with id "A" is linked to work with id "B"
  // works comprised of linked works are identified by compound id together with their linked works.
  // A+B:(A->B, B) means compound work with id "A+B" is made of work A linked to B and work B.

  describe("Adding links without existing works") {
    it("updating nothing with A gives A:A") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("A", Set.empty),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(WorkNode("A", List(), "A"))
    }

    it("updating nothing with A->B gives A+B:A->B") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("A", Set("B")),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B"),
        WorkNode("B", List(), "A+B"))
    }

    it("updating nothing with B->A gives A+B:B->A") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set("A")),
          existingGraph = WorkGraph(Set.empty)
        )
        .nodes shouldBe Set(
        WorkNode("B", List("A"), "A+B"),
        WorkNode("A", List(), "A+B"))
    }
  }

  describe("Adding links to existing works") {
    it("updating A->B with A->B gives A+B:(A->B, B)") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("A", Set("B")),
          existingGraph = WorkGraph(Set(WorkNode("A", List("B"), "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B"),
        WorkNode("B", List(), "A+B"))
    }

    it("updating A->B with B->C gives A+B+C:(A->B, B->C, C)") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set("C")),
          existingGraph = WorkGraph(Set(WorkNode("A", List("B"), "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B+C"),
        WorkNode("B", List("C"), "A+B+C"),
        WorkNode("C", List(), "A+B+C")
      )
    }

    it("updating A->B, C->D with B->C gives A+B+C+D:(A->B, B->C, C->D, D)") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set("C")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", List("B"), "A+B"),
              WorkNode("C", List("D"), "C+D")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B+C+D"),
        WorkNode("B", List("C"), "A+B+C+D"),
        WorkNode("C", List("D"), "A+B+C+D"),
        WorkNode("D", List(), "A+B+C+D"))
    }

    it("updating A->B with B->[C,D] gives A+B+C+D:(A->B, B->C&D, C, D") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set("C", "D")),
          existingGraph = WorkGraph(Set(WorkNode("A", List("B"), "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B+C+D"),
        WorkNode("B", List("C", "D"), "A+B+C+D"),
        WorkNode("C", List(), "A+B+C+D"),
        WorkNode("D", List(), "A+B+C+D"))
    }

    it("updating A->B->C with A->C gives A+B+C:(A->B, B->C, C->A") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("C", Set("A")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", List("B"), "A+B"),
              WorkNode("B", List("C"), "B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B+C"),
        WorkNode("B", List("C"), "A+B+C"),
        WorkNode("C", List("A"), "A+B+C")
      )
    }
  }

  describe("Removing links") {
    it("updating  A->B, B with A gives A:A and B:B") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("A", Set.empty),
          existingGraph = WorkGraph(
            Set(WorkNode("A", List("B"), "A+B"), WorkNode("B", List(), "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List(), "A"),
        WorkNode("B", List(), "B"))
    }

    it(
      "updating A->B with A but NO B (*should* not be possible) gives A:A and B:B") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("A", Set.empty),
          existingGraph = WorkGraph(Set(WorkNode("A", List("B"), "A+B")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List(), "A"),
        WorkNode("B", List(), "B"))
    }

    it("updating A->B->C with B gives A+B:(A->B, B) and C:C") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set.empty),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", List("B"), "A+B+C"),
              WorkNode("B", List("C"), "A+B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B"),
        WorkNode("B", List(), "A+B"),
        WorkNode("C", List(), "C"))
    }

    it("updating A<->B->C with B->C gives A+B+C:(A->B, B->C, C)") {
      LinkedWorkGraphUpdater
        .update(
          workUpdate = WorkUpdate("B", Set("C")),
          existingGraph = WorkGraph(
            Set(
              WorkNode("A", List("B"), "A+B+C"),
              WorkNode("B", List("A", "C"), "A+B+C"),
              WorkNode("C", List(), "A+B+C")))
        )
        .nodes shouldBe Set(
        WorkNode("A", List("B"), "A+B+C"),
        WorkNode("B", List("C"), "A+B+C"),
        WorkNode("C", List(), "A+B+C"))
    }
  }
}
