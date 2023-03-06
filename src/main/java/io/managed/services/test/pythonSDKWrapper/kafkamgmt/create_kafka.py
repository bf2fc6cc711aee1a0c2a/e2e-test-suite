#!/usr/bin/python

import argparse
import os
from pprint import pprint
import dotenv

import auth.rhoas_auth as auth
import rhoas_kafka_mgmt_sdk
from rhoas_kafka_mgmt_sdk.api import default_api
from rhoas_kafka_mgmt_sdk.model.kafka_request_payload import KafkaRequestPayload

parser = argparse.ArgumentParser()
parser.add_argument('--kafka_name', help='Kafka name', required=True)
parser.add_argument('--cloud_provider', help='Cloud provider', required=True)
parser.add_argument('--region', help='Region for instance', required=True) 
parser.add_argument('--reauthentication_enabled', help='Reauthentication enabled', required=False, default=True)
parser.add_argument('--plan', help='Plan', required=True)
parser.add_argument('--billing_model', help='Billing model', required=False)
parser.add_argument('--billing_cloud_account_id', help='Billing cloud account id', required=False)
parser.add_argument('--marketplace', help='Marketplace', required=False)
parser.add_argument('--instance_type', help='Instance type', required=False)
parser.add_argument('--env_file', help='The env file', required=False, default='x.config.env')
args = parser.parse_args()

dotenv.load_dotenv(args.env_file)
offline_token = os.environ.get('OFFLINE_TOKEN')
openshift_base_uri = os.environ.get('OPENSHIFT_BASE_URI')

token = {}
token = auth.get_access_token(offline_token)

configuration = rhoas_kafka_mgmt_sdk.Configuration(
    host = openshift_base_uri,
    access_token = token['access_token']
)

with rhoas_kafka_mgmt_sdk.ApiClient(configuration) as api_client:
    # Create an instance of the API class
    api_instance = default_api.DefaultApi(api_client)
    _async = True # bool | Perform the action in an asynchronous manner
    kafka_request_payload = KafkaRequestPayload(
        cloud_provider=args.cloud_provider,
        name=args.kafka_name,
        region=args.region,
        reauthentication_enabled=args.reauthentication_enabled,
        plan=args.plan,
        billing_cloud_account_id=args.billing_cloud_account_id,
        marketplace=args.marketplace,
        billing_model=args.billing_model,
    ) # KafkaRequestPayload | Kafka data

    try:
        api_response = api_instance.create_kafka(_async, kafka_request_payload)
        pprint(api_response)
    except rhoas_kafka_mgmt_sdk.ApiException as e:
        print("Exception when calling DefaultApi->create_kafka: %s\n" % e)
