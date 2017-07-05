#!/usr/bin/env bash
# This script builds the Docker images for our nginx containers and
# pushes them to S3.  Takes a single argument: either BUILD or DEPLOY.

set -o errexit
set -o nounset

TASK=${1:-BUILD}
echo "*** Task is $TASK"

export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-eu-west-1}

# Directory name of the script itself
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

# Log in to ECR so that we can do 'docker push'
if [[ "$TASK" == "DEPLOY" ]]
then
  $(aws ecr get-login)
fi

for conf_file in *.nginx.conf
do
  variant="$(echo "$conf_file" | tr '.' ' ' | awk '{print $1}')"
  echo "*** Building nginx image for $variant..."

  # All out nginx containers have a dedicated repository in ECR.  We need the
  # URI for pushing the containers.
  export ECR_URI=$(
    aws ecr describe-repositories --repository-name "uk.ac.wellcome/nginx_$variant" | \
    jq -r '.repositories[0].repositoryUri')
  echo "*** ECR_URI is $ECR_URI"

  if [[ "$ECR_URI" == "" ]]
  then
    echo "*** Failed to read ECR repo information" >&2
    exit 1
  fi

  # Construct the tag used for the image
  TAG="$ECR_URI:$(git rev-parse HEAD)"
  echo "*** Image will be tagged $TAG"

  # Actually build the image
  docker build --build-arg conf_file="$conf_file" --tag "$TAG" .

  if [[ "$TASK" == "DEPLOY" ]]
  then
    docker push "$TAG"
  fi
done
