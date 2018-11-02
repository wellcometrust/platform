package uk.ac.wellcome.platform.archive.registrar.http.models
import java.net.URL

import io.circe.generic.extras.JsonKey
import uk.ac.wellcome.platform.archive.common.models.{DisplayLocation, DisplayStorageSpace}
import uk.ac.wellcome.platform.archive.registrar.common.models._

case class DisplayBag(
                       @JsonKey("@context")
  context: String,
  id: String,
  space: DisplayStorageSpace,
  info: DisplayBagInfo,
  manifest: DisplayBagManifest,
  accessLocation: DisplayLocation,
  createdDate: String,
  @JsonKey("type")
  ontologyType: String = "Bag"
)

object DisplayBag {
  def apply(storageManifest: StorageManifest, contextUrl: URL): DisplayBag = DisplayBag(
    contextUrl.toString,
    storageManifest.id.toString,
    DisplayStorageSpace(storageManifest.space.underlying),
    DisplayBagInfo(storageManifest.info),
    DisplayBagManifest(storageManifest.manifest),
    DisplayLocation(storageManifest.accessLocation),
    storageManifest.createdDate.toString
  )
}
