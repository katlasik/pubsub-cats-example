import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import cats.effect.IO
import cats.effect.Resource
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import fs2.Stream

final class Consumer private (queue: Queue[IO, PubSubMessage]):
  def poll: IO[Option[PubSubMessage]] = queue.tryTake

  val stream: Stream[IO, PubSubMessage] = Stream.fromQueueUnterminated(queue)

object Consumer:

  private def receiver(
      dispatcher: Dispatcher[IO],
      queue: Queue[IO, PubSubMessage]
  ): MessageReceiver = (message, consumer) => {
    val p = for {
      payload <- IO(message.getData().toStringUtf8())
      _ <- queue.offer(
        PubSubMessage(message.getMessageId(), payload, IO(consumer.ack()))
      )
    } yield ()
    dispatcher.unsafeRunSync(p)
  }

  def create(subscriptionName: String) = for {
    queue <- Resource.eval(Queue.unbounded[IO, PubSubMessage])
    dispatcher <- Dispatcher[IO]
    subscriber <- Resource.eval(
      IO(
        Subscriber
          .newBuilder(subscriptionName, receiver(dispatcher, queue))
          .build()
      )
    )
    _ <- Resource.make(
      IO(subscriber.startAsync())
    )(_ => IO(subscriber.stopAsync()))
  } yield Consumer(queue)
