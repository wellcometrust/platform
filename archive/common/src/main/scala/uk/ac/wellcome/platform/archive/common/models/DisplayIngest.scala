package uk.ac.wellcome.platform.archive.common.models

import java.util.UUID

import io.circe.generic.extras.JsonKey
import uk.ac.wellcome.platform.archive.common.progress.models.{
  Callback,
  Progress,
  ProgressEvent,
  Resource
}

sealed trait DisplayIngest

case class RequestDisplayIngest(sourceLocation: DisplayLocation,
                                callback: Option[DisplayCallback],
                                ingestType: DisplayIngestType,
                                space: DisplayStorageSpace,
                                @JsonKey("type")
                                ontologyType: String = "Ingest")
    extends DisplayIngest

case class ResponseDisplayIngest(id: UUID,
                                 sourceLocation: DisplayLocation,
                                 callback: Option[DisplayCallback],
                                 ingestType: DisplayIngestType,
                                 space: DisplayStorageSpace,
                                 status: DisplayStatus,
                                 resources: Seq[DisplayIngestResource] =
                                   Seq.empty,
                                 events: Seq[DisplayProgressEvent] = Seq.empty,
                                 createdDate: String,
                                 lastModifiedDate: String,
                                 @JsonKey("type")
                                 ontologyType: String = "Ingest")
    extends DisplayIngest

case class DisplayCallback(url: String,
                           status: Option[DisplayStatus],
                           @JsonKey("type")
                           ontologyType: String = "Callback")

case class DisplayIngestType(id: String = "create",
                             @JsonKey("type")
                             ontologyType: String = "IngestType")

case class DisplayIngestResource(id: String,
                                 @JsonKey("type")
                                 ontologyType: String = "IngestResource")

case class DisplayStorageSpace(id: String,
                               @JsonKey("type")
                               ontologyType: String = "Space")

case class DisplayStatus(id: String,
                               @JsonKey("type")
                               ontologyType: String = "Status")

case class DisplayProgressEvent(description: String,
                                createdDate: String,
                                @JsonKey("type")
                                ontologyType: String = "ProgressEvent")

case object ResponseDisplayIngest {
  def apply(progress: Progress): ResponseDisplayIngest = ResponseDisplayIngest(
    id = progress.id,
    sourceLocation = DisplayLocation(progress.sourceLocation),
    callback = progress.callback.map(DisplayCallback(_)),
    space = DisplayStorageSpace(progress.space.toString),
    ingestType = DisplayIngestType(),
    resources = progress.resources.map(DisplayIngestResource(_)),
    status = DisplayStatus(progress.status),
    events = progress.events.map(DisplayProgressEvent(_)),
    createdDate = progress.createdDate.toString,
    lastModifiedDate = progress.lastModifiedDate.toString
  )
}

case object DisplayProgressEvent {
  def apply(progressEvent: ProgressEvent): DisplayProgressEvent =
    DisplayProgressEvent(
      progressEvent.description,
      progressEvent.createdDate.toString)
}

case object DisplayStatus {
  def apply(progressStatus: Progress.Status): DisplayStatus =
    DisplayStatus(progressStatus.toString)

  def apply(callbackStatus: Callback.CallbackStatus): DisplayStatus =
    DisplayStatus(callbackStatus.toString)
}

case object DisplayIngestResource {
  def apply(resource: Resource): DisplayIngestResource =
    DisplayIngestResource(resource.id.underlying)
}

case object DisplayCallback {
  def apply(callback: Callback): DisplayCallback = DisplayCallback(
    callback.uri.toString,
    Some(DisplayStatus(callback.status))
  )
}
