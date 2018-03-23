package uk.ac.wellcome.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.test.utils.JsonTestUtil
import uk.ac.wellcome.utils.JsonUtil._

class UnidentifiedWorkTest extends FunSpec with Matchers with JsonTestUtil {

  private val license_CCBYJson =
    s"""{
            "licenseType": "${License_CCBY.licenseType}",
            "label": "${License_CCBY.label}",
            "url": "${License_CCBY.url}",
            "ontologyType": "License"
          }"""

  // TRIVIA: This is an extract from Marco Polo's diaries, in which he
  // mistakes a rhinoceros for a unicorn.
  val physicalDescription =
    "Hair like that of a buffalo, feet like those of an elephant"

  // TRIVIA: This is based on Harry Potter, when we first meet Dobby.
  val extent = "Both socks pulled up to their highest extent"

  // TRIVIA: on 3 July 1998, LNER 4468 "Mallard" set the world speed record
  // for steam locomotives, reaching 126 mph.
  val publicationDate = "3 July 1938"

  // Reskitkish is a fictional language from the "Wayfarers" series of novels,
  // and requires large lungs to speak effectively.
  val language = Language(
    id = "res",
    label = "Reskitkish"
  )

  val unidentifiedWorkJson: String =
    s"""
      |{
      |  "title": "title",
      |  "sourceIdentifier": {
      |    "identifierScheme": "${IdentifierSchemes.miroImageNumber.toString}",
      |    "value": "value"
      |  },
      |  "version": 1,
      |  "identifiers": [
      |    {
      |      "identifierScheme": "${IdentifierSchemes.miroImageNumber.toString}",
      |      "value": "value"
      |    }
      |  ],
      |  "workType": {
      |    "id": "id",
      |    "label": "label",
      |    "ontologyType" : "WorkType"
      |  },
      |  "description": "description",
      |  "physicalDescription": "$physicalDescription",
      |  "extent": "$extent",
      |  "lettering": "lettering",
      |  "createdDate": {
      |    "label": "period",
      |    "ontologyType": "Period"
      |  },
      |  "subjects": [
      |    {
      |      "label": "subject",
      |      "ontologyType": "Concept",
      |      "qualifierType": null,
      |      "type": "Concept"
      |    }
      |  ],
      |  "creators": [
      |    {
      |      "label": "47",
      |      "ontologyType": "Agent"
      |    }
      |  ],
      |  "genres": [
      |    {
      |      "label": "genre",
      |      "ontologyType": "Concept",
      |      "qualifierType": null,
      |      "type": "Concept"
      |    }
      |  ],
      |  "thumbnail": {
      |    "locationType": "location",
      |    "url" : "",
      |    "credit" : null,
      |    "license": $license_CCBYJson,
      |    "type": "DigitalLocation",
      |    "ontologyType": "DigitalLocation"
      |  },
      |  "items": [
      |    {
      |      "sourceIdentifier": {
      |        "identifierScheme": "${IdentifierSchemes.miroImageNumber.toString}",
      |        "value": "value"
      |      },
      |      "identifiers": [
      |        {
      |          "identifierScheme": "${IdentifierSchemes.miroImageNumber.toString}",
      |          "value": "value"
      |        }
      |      ],
      |      "locations": [
      |        {
      |          "locationType": "location",
      |          "url" : "",
      |          "credit" : null,
      |          "license": $license_CCBYJson,
      |          "type": "DigitalLocation",
      |          "ontologyType": "DigitalLocation"
      |        }
      |      ],
      |      "ontologyType": "Item"
      |    }
      |  ],
      |  "publishers": [
      |    {
      |      "label": "MIT Press",
      |      "type": "Organisation",
      |      "ontologyType": "Organisation"
      |    }
      |  ],
      |  "visible":true,
      |  "publicationDate": {
      |    "label": "$publicationDate",
      |    "ontologyType": "Period"
      |  },
      |  "placesOfPublication": [
      |   {
      |     "label": "Madrid",
      |     "ontologyType": "Place"
      |   }
      |  ],
      |   "language": {
      |     "id": "${language.id}",
      |     "label": "${language.label}",
      |     "ontologyType": "Language"
      |   },
      |  "ontologyType": "Work"
      |}
    """.stripMargin

  val location = DigitalLocation(
    locationType = "location",
    url = "",
    license = License_CCBY
  )

  val identifier = SourceIdentifier(
    identifierScheme = IdentifierSchemes.miroImageNumber,
    value = "value"
  )

  val item = UnidentifiedItem(
    sourceIdentifier = identifier,
    identifiers = List(identifier),
    locations = List(location)
  )

  val publisher = Organisation(
    label = "MIT Press"
  )

  val publishers = List(publisher)

  val workType = WorkType(
    id = "id",
    label = "label"
  )

  val unidentifiedWork = UnidentifiedWork(
    title = Some("title"),
    sourceIdentifier = identifier,
    version = 1,
    identifiers = List(identifier),
    workType = Some(workType),
    description = Some("description"),
    physicalDescription = Some(physicalDescription),
    extent = Some(extent),
    lettering = Some("lettering"),
    createdDate = Some(Period("period")),
    subjects = List(Concept("subject")),
    creators = List(Agent("47")),
    genres = List(Concept("genre")),
    thumbnail = Some(location),
    items = List(item),
    publishers = publishers,
    publicationDate = Some(Period(publicationDate)),
    placesOfPublication = List(Place("Madrid")),
    language = Some(language)
  )

  it("should serialise an unidentified Work as JSON") {
    val result = toJson(unidentifiedWork)

    result.isSuccess shouldBe true

    assertJsonStringsAreEqual(result.get, unidentifiedWorkJson)
  }

  it("should deserialize a JSON string as a unidentified Work") {
    val result = fromJson[UnidentifiedWork](unidentifiedWorkJson)

    result.isSuccess shouldBe true
    result.get shouldBe unidentifiedWork
  }

  it("should have an ontology type 'Work' when serialised to JSON") {
    val jsonString = toJson(unidentifiedWork).get

    jsonString.contains("""ontologyType":"Work"""") should be(true)
  }
}
