#!/bin/bash -eu

sbt docker:publishLocal
heroku container:login
docker tag namazu-notification-chatwork registry.heroku.com/n2chatwork/web
docker push registry.heroku.com/n2chatwork/web
heroku container:release web -a n2chatwork
