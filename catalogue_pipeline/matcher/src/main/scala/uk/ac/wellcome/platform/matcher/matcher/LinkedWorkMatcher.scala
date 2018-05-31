package uk.ac.wellcome.platform.matcher.matcher

import com.google.inject.Inject
import uk.ac.wellcome.models.matcher.{EquivalentIdentifiers, MatchResult}
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.platform.matcher.models._
import uk.ac.wellcome.platform.matcher.storage.WorkGraphStore
import uk.ac.wellcome.platform.matcher.workgraph.LinkedWorkGraphUpdater
import uk.ac.wellcome.storage.GlobalExecutionContext._

import scala.concurrent.Future

class LinkedWorkMatcher @Inject()(workGraphStore: WorkGraphStore) {
  def matchWork(work: UnidentifiedWork): Future[MatchResult] =
    matchLinkedWorks(work).map { MatchResult(_) }

  private def matchLinkedWorks(
    work: UnidentifiedWork): Future[Set[EquivalentIdentifiers]] = {
    val workNodeUpdate = WorkNodeUpdate(work)

    for {
      linkedWorksGraph <- workGraphStore.findAffectedWorks(workNodeUpdate)
      updatedLinkedWorkGraph = LinkedWorkGraphUpdater.update(
        workNodeUpdate,
        linkedWorksGraph)
      _ <- workGraphStore.put(updatedLinkedWorkGraph)

    } yield {
      findEquivalentIdentifierSets(updatedLinkedWorkGraph)
    }
  }

  private def findEquivalentIdentifierSets(workGraph: WorkGraph): Set[EquivalentIdentifiers] =
    workGraph.nodes
      .groupBy { _.componentId }
      .values
      .map { (nodeSet: Set[WorkNode]) => nodeSet.map { _.id }}
      .map { (nodeIds: Set[String]) => EquivalentIdentifiers(nodeIds) }
      .toSet
}
