package uk.ac.wellcome.test.fixtures

import com.amazonaws.auth._
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.sqs._
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import scala.util.Random

trait SqsFixtures {

  private val sqsEndpointUrl = "http://localhost:9324"

  private val accessKey = "access"
  private val secretKey = "secret"

  val sqsLocalFlags = Map(
    "aws.sqs.endpoint" -> sqsEndpointUrl,
    "aws.sqs.accessKey" -> accessKey,
    "aws.sqs.secretKey" -> secretKey,
    "aws.region" -> "localhost"
  )

  val sqsClient: AmazonSQS = AmazonSQSClientBuilder
    .standard()
    .withCredentials(new AWSStaticCredentialsProvider(
      new BasicAWSCredentials(accessKey, secretKey)))
    .withEndpointConfiguration(
      new EndpointConfiguration(sqsEndpointUrl, "localhost"))
    .build()

  def withLocalSqsQueue(testWith: TestWith[String]) = {
    val queueName = Random.alphanumeric take 10 mkString
    val url = sqsClient.createQueue(queueName).getQueueUrl

    try {
      testWith(url)
    } finally {
      sqsClient.purgeQueue(new PurgeQueueRequest().withQueueUrl(url))
    }
  }

}
