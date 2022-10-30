import cats.effect.IOApp
import cats.effect.ExitCode
import cats.effect.IO
import cats.syntax.all.*
import scala.concurrent.duration.*

val TopicName = "projects/pubsub-366208/topics/messages"
val SubscriptionNamePolling =
  "projects/pubsub-366208/subscriptions/messages-sub-polling"
val SubscriptionNameStream =
  "projects/pubsub-366208/subscriptions/messages-sub-stream"

object App extends IOApp:

  val publisher = Publisher
    .create(TopicName)
    .onFinalize(IO.println("Publisher has closed."))
    .use(publisher =>
      val p = for
        message <- IO.readLine
        _ <- publisher
          .publish(message)
          .flatMap(messageId =>
            IO.println(
              s"Published new message: payload = [${message}], id = [$messageId]."
            )
          )
          .whenA(message.nonEmpty)
      yield ()

      IO.println("Please type message and press [Enter] to send.") *> p.foreverM
    )

  val consumerPolling = Consumer
    .create(SubscriptionNamePolling)
    .onFinalize(IO.println("Consumer using polling has closed."))
    .use(c =>
      def loop: IO[Unit] = for
        maybeMessage <- c.poll
        _ <- maybeMessage match
          case Some(PubSubMessage(id, payload, ack)) =>
            IO.println(
              s"Received message by polling: payload = [$payload], id = [$id]."
            ) *> ack
          case None => IO.sleep(1.seconds)
        _ <- loop
      yield ()

      loop
    )

  val consumerStream = Consumer
    .create(SubscriptionNameStream)
    .onFinalize(IO.println("Consumer using stream has closed."))
    .use(c =>
      c.stream
        .evalTap { case PubSubMessage(id, payload, ack) =>
          IO.println(
            s"Received message from the stream: payload = [$payload], id = [$id]."
          ) *> ack
        }
        .compile
        .drain
    )

  override def run(args: List[String]): IO[ExitCode] = (
    publisher,
    consumerPolling,
    consumerStream
  ).parTupled.as(ExitCode.Success)