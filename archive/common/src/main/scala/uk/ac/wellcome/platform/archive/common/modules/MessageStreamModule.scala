package uk.ac.wellcome.platform.archive.common.modules

import com.amazonaws.services.sqs.AmazonSQSAsync
import com.google.inject.{AbstractModule, Provides}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSConfig
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.platform.archive.common.messaging.MessageStream

object MessageStreamModule extends AbstractModule {

  @Provides
  def providesMessageStream(sqsClient: AmazonSQSAsync,
                            sqsConfig: SQSConfig,
                            metricsSender: MetricsSender) = {
    new MessageStream[NotificationMessage, Unit](
      sqsClient,
      sqsConfig,
      metricsSender
    )
  }
}
