#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""
Parse image records from a Miro export and push them into a DynamoDB table.

Usage:
  miro_adapter.py --table=<TABLE> --collection=<COLLECTION> --bucket=<BUCKET> --key=<KEY>
  miro_adapter.py -h | --help

Options:
  -h --help                   Show this screen.
  --table=<TABLE>             DynamoDB table to write the Miro data to.
  --collection=<COLLECTION>   Name of the associated Miro images collection.
  --bucket=<BUCKET>           S3 bucket containing the Miro XML dumps.
  --key=<KEY>                 Key of the Miro XML dump in the S3 bucket.

"""

import json

import boto3
import docopt
from lxml import etree

from utils import elem_to_dict, fix_miro_xml_entities


def parse_image_data(xml_string):
    """
    Given an XML string, generate blobs of data for each image.
    """
    # Within the Miro XML file, each image is stored inside in the
    # following format:
    #
    #   <image>
    #     <key1>value1</key1>
    #     <key2>value1</key2>
    #     ...
    #   </image>
    #
    # so we want to grab the <image> tags, and export data from their
    # children attributes.
    root = etree.fromstring(xml_string)
    for child in root.findall('image'):
        yield elem_to_dict(child)


def push_to_dynamodb(table_name, collection_name, image_data):
    """
    Given the name of a Dynamo table and some image data, push it
    into DynamoDB.
    """
    dynamodb = boto3.resource('dynamodb')
    table = dynamodb.Table(table_name)

    with table.batch_writer() as batch:
        for idx, image in enumerate(image_data, start=1):
            print('Pushing image with ID %s' % image['image_no_calc'])
            batch.put_item(
                Item={
                    'MiroID': image['image_no_calc'],
                    'MiroCollection': collection_name,
                    'ReindexShard': 'default',
                    'ReindexVersion': 0,
                    'data': json.dumps(image, separators=(',', ':'))
                }
            )
        print('Written %d records to DynamoDB' % idx)


def read_from_s3(bucket, key):
    client = boto3.client('s3')
    obj = client.get_object(Bucket=bucket, Key=key)
    return obj['Body'].read()


if __name__ == '__main__':
    args = docopt.docopt(__doc__)
    xml_string = fix_miro_xml_entities(
        read_from_s3(bucket=args['--bucket'], key=args['--key'])
    )

    image_data = parse_image_data(xml_string)
    push_to_dynamodb(
        table_name=args['--table'],
        collection_name=args['--collection'],
        image_data=image_data
    )
