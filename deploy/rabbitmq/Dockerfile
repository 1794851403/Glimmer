ARG RABBITMQ_VERSION=3.13.7
FROM rabbitmq:${RABBITMQ_VERSION}-management

ARG DELAYED_PLUGIN_VERSION=3.13.0

# 延迟消息插件：秒杀 30 分钟关单依赖它。
# 如果 VM 无法访问 GitHub，可手动下载同名 .ez 放到本目录，改用 COPY。
ADD https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v${DELAYED_PLUGIN_VERSION}/rabbitmq_delayed_message_exchange-${DELAYED_PLUGIN_VERSION}.ez /opt/rabbitmq/plugins/

RUN rabbitmq-plugins enable --offline rabbitmq_delayed_message_exchange
