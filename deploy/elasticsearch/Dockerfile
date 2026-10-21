ARG ES_VERSION=8.15.0
FROM docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}

ARG IK_VERSION=8.15.0

# IK 分词插件版本必须与 ES 完全一致。
# 如果 VM 无法访问 GitHub，可手动下载 zip 放到本目录，改用 COPY + elasticsearch-plugin install file:///...
RUN bin/elasticsearch-plugin install --batch \
    https://github.com/infinilabs/analysis-ik/releases/download/v${IK_VERSION}/elasticsearch-analysis-ik-${IK_VERSION}.zip
