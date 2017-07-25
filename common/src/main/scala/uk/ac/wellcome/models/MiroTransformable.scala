package uk.ac.wellcome.models

import scala.util.Try

import com.fasterxml.jackson.annotation.JsonProperty

import uk.ac.wellcome.utils.JsonUtil

case class MiroTransformableData(
  @JsonProperty("image_title") title: Option[String],
  @JsonProperty("image_creator") creator: Option[List[String]],
  @JsonProperty("image_image_desc") description: Option[String],
  @JsonProperty("image_secondary_creator") secondaryCreator: Option[
    List[String]],
  @JsonProperty("image_artwork_date") artworkDate: Option[String],
  @JsonProperty("image_cleared") cleared: Option[String],
  @JsonProperty("image_copyright_cleared") copyright_cleared: Option[String]
)

case class ShouldNotTransformException(message: String)
    extends Exception(message)

case class MiroTransformable(MiroID: String,
                             MiroCollection: String,
                             data: String,
                             ReindexShard: String = "default",
                             ReindexVersion: Int = 0)
    extends Transformable
    with Reindexable[String] {

  val id: ItemIdentifier[String] = ItemIdentifier(
    HashKey("MiroID", MiroID),
    RangeKey("MiroCollection", MiroCollection)
  )

  override def transform: Try[Work] =
    JsonUtil.fromJson[MiroTransformableData](data).map { miroData =>
      // Identifier is passed straight through
      val identifiers = List(SourceIdentifier("Miro", "MiroID", MiroID))

      // XML tags refer to fields within the Miro XML dumps.

      // If the <image_cleared> or <image_copyright_cleared> fields are
      // missing or don't have have the value 'Y', then we shouldn't expose
      // this image in the public API.
      // See https://github.com/wellcometrust/platform-api/issues/356
      if (miroData.cleared.getOrElse("N") != "Y") {
        throw new ShouldNotTransformException("image_cleared field is not Y")
      }
      if (miroData.copyright_cleared.getOrElse("N") != "Y") {
        throw new ShouldNotTransformException(
          "image_copyright_cleared field is not Y")
      }

      // Populate the label and description.  The rules are as follows:
      //
      //  1.  For V images, if the first line of <image_image_desc> is a
      //      prefix of <image_title>, we use that instead of the title, and
      //      drop the first line of the description.
      //      If it's a single-line description, drop the description entirely.
      //  2.  Otherwise, use the <image_title> ("short description") and
      //      <image_image_desc> ("description") fields.
      //
      // In at least the V collection, many of the titles are truncated forms
      // of the description field -- and we don't want to repeat information
      // in the public API.
      //
      // Note: Every image in the V collection that has image_cleared == Y has
      // non-empty title.  This is _not_ true for the MIRO records in general.
      // TODO: Work out what label to use for those records.
      //
      val candidateLabel = miroData.description.getOrElse("").split("\n").head
      val titleIsTruncatedDescription = candidateLabel
        .startsWith(miroData.title.get)

      val useDescriptionAsLabel = (titleIsTruncatedDescription &&
        MiroCollection == "Images-V")

      val label =
        if (useDescriptionAsLabel) candidateLabel
        else miroData.title.get

      val description = if (useDescriptionAsLabel) {
        // Remove the first line from the description, and trim any extra
        // whitespace (leading newlines)
        val candidateDescription = miroData.description.get
          .replace(candidateLabel, "")
          .trim
        if (candidateDescription.length > 0) Some(candidateDescription)
        else None
      } else {
        miroData.description match {
          case Some(d) => if (d.trim.length > 0) Some(d) else None
          case None => None
        }
      }

      // <image_creator>: the Creator, which maps to our property "hasCreator"
      val creators: List[Agent] = miroData.creator match {
        case Some(c) => c.map { Agent(_) }
        case None => List()
      }

      // <image_secondary_creator>: what MIRO calls Secondary Creator, which
      // will also just have to map to our object property "hasCreator"
      val secondaryCreators: List[Agent] = miroData.secondaryCreator match {
        case Some(c) => c.map { Agent(_) }
        case None => List()
      }

      // Determining the creation date depends on several factors, so we do
      // it on a per-collection basis.
      val createdDate: Option[Period] = MiroCollection match {
        case "Images-V" => miroData.artworkDate.map { Period(_) }
        case _ => None
      }

      Work(
        identifiers = identifiers,
        label = label,
        description = description,
        createdDate = createdDate,
        creators = creators ++ secondaryCreators
      )
    }
}
