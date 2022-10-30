import cats.effect.IO

final case class PubSubMessage(id: String, payload: String, ack: IO[Unit])
