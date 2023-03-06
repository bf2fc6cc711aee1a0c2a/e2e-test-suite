import argparse
import os
import time
import rhoas_kafka_mgmt_sdk
from rhoas_kafka_mgmt_sdk.api import default_api
from rhoas_kafka_mgmt_sdk.model.error import Error
from pprint import pprint
import auth.rhoas_auth as auth
import dotenv

parser = argparse.ArgumentParser()
parser.add_argument('--kafka_id', help='The ID of the Kafka instance', required=True)
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
    api_instance = default_api.DefaultApi(api_client)
    id = args.kafka_id # str | The ID of record
    _async = True # bool | Perform the action in an asynchronous manner
    try:
        api_response = api_instance.delete_kafka_by_id(id, _async)
        pprint(api_response)
    except rhoas_kafka_mgmt_sdk.ApiException as e:
        print("Exception when calling DefaultApi->delete_kafka_by_id: %s\n" % e)