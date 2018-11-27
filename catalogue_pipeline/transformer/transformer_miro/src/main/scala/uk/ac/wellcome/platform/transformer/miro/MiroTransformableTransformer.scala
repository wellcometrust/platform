package uk.ac.wellcome.platform.transformer.miro

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.exceptions.ShouldNotTransformException
import uk.ac.wellcome.platform.transformer.miro.models.MiroTransformable
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

import scala.util.Try

class MiroTransformableTransformer
    extends transformers.MiroContributors
    with transformers.MiroCreatedDate
    with transformers.MiroItems
    with transformers.MiroGenres
    with transformers.MiroIdentifiers
    with transformers.MiroSubjects
    with transformers.MiroThumbnail
    with transformers.MiroTitleAndDescription
    with transformers.MiroWorkType
    with Logging {

  def transform(
    transformable: MiroTransformable,
    version: Int
  ): Try[TransformedBaseWork] = {
    val miroRecord = MiroRecord.create(transformable.data)
    doTransform(miroRecord, version) map { transformed =>
      debug(s"Transformed record to $transformed")
      transformed
    } recover {
      case e: Throwable =>
        error("Failed to perform transform to unified item", e)
        throw e
    }
  }

  private def doTransform(miroRecord: MiroRecord, version: Int): Try[TransformedBaseWork] = {
    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType("miro-image-number"),
      ontologyType = "Work",
      value = miroRecord.imageNumber
    )

    Try {
      // These images should really have been removed from the pipeline
      // already, but we have at least one instance (B0010525).  It was
      // throwing a MatchError when we tried to pick a license, so handle
      // it properly here.
      if (!miroRecord.copyrightCleared.contains("Y")) {
        throw new ShouldNotTransformException(
          s"Image ${miroRecord.imageNumber} does not have copyright clearance!"
        )
      }

      val (title, description) = getTitleAndDescription(miroRecord)

      UnidentifiedWork(
        sourceIdentifier = sourceIdentifier,
        otherIdentifiers =
          getOtherIdentifiers(miroRecord, miroRecord.imageNumber),
        mergeCandidates = List(),
        title = title,
        workType = getWorkType,
        description = description,
        physicalDescription = None,
        extent = None,
        lettering = miroRecord.suppLettering,
        createdDate =
          getCreatedDate(miroRecord, miroId = miroRecord.imageNumber),
        subjects = getSubjects(miroRecord),
        genres = getGenres(miroRecord),
        contributors = getContributors(
          miroId = miroRecord.imageNumber,
          miroRecord = miroRecord
        ),
        thumbnail = Some(getThumbnail(miroRecord, miroRecord.imageNumber)),
        production = List(),
        language = None,
        dimensions = None,
        items = getItems(miroRecord, miroRecord.imageNumber),
        itemsV1 = getItemsV1(miroRecord, miroRecord.imageNumber),
        version = version
      )
    }.recover {
      case e: ShouldNotTransformException =>
        info(s"Should not transform: ${e.getMessage}")
        UnidentifiedInvisibleWork(
          sourceIdentifier = sourceIdentifier,
          version = version
        )
    }
  }
}
