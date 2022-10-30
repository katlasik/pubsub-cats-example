import com.google.cloud.pubsub.v1.{Publisher => GooglePublisher}
import com.google.pubsub.v1.PubsubMessage
import cats.effect.IO
import cats.effect.Resource
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import com.google.api.core.ApiFutures
import com.google.api.core.ApiFutureCallback
import com.google.protobuf.ByteString
import cats.syntax.all.*

final class Publisher private (
    internal: GooglePublisher,
    executor: ExecutorService
):

  def publish(message: String): IO[String] = for {
    pubSubMessage <- IO(
      PubsubMessage
        .newBuilder()
        .setData(ByteString.copyFromUtf8(message))
        .build()
    )
    future = internal.publish(pubSubMessage)
    messageId <- IO.async[String] {
      (callback: Either[Throwable, String] => Unit) =>
        IO {
          ApiFutures.addCallback(
            future,
            new ApiFutureCallback[String]() {
              override def onFailure(error: Throwable): Unit =
                callback(error.asLeft)

              override def onSuccess(messageId: String): Unit =
                callback(messageId.asRight)
            },
            executor
          )

          Some(IO(future.cancel(false)))
        }
    }
  } yield messageId

object Publisher:
  def create(topicName: String): Resource[IO, Publisher] = for {
    executor <- Resource.make(
      IO(Executors.newCachedThreadPool())
    )(ec => IO(ec.shutdown()) *> IO(ec.awaitTermination(10, TimeUnit.SECONDS)))
    googlePublisher <- Resource.make(
      IO(GooglePublisher.newBuilder(topicName).build())
    )(p =>
      IO(p.shutdown()) *> IO.interruptible(
        p.awaitTermination(10, TimeUnit.SECONDS)
      )
    )
  } yield Publisher(googlePublisher, executor)
