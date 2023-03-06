import argparse
import os
import time
import rhoas_kafka_mgmt_sdk
from rhoas_kafka_mgmt_sdk.api import default_api
from rhoas_kafka_mgmt_sdk.model.error import Error
from rhoas_kafka_mgmt_sdk.model.kafka_request_list import KafkaRequestList
from pprint import pprint
import dotenv
import auth.rhoas_auth as auth
from utils.sanitize_output import clean_datetime_bool

dotenv.load_dotenv()

parser = argparse.ArgumentParser()

parser.add_argument('--page', help='Page number', required=False, default='1')
parser.add_argument('--size', help='Page size', required=False, default='100')
parser.add_argument('--order_by', help='Order by', required=False, default='name asc')
parser.add_argument('--env_file', help='The env file', required=False, default='x.config.env')
parser.add_argument('--search', help='Search', required=False, default='')
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
    page = args.page # str | Page index (optional)
    size = args.size # str | Number of items in each page (optional)
    order_by = args.order_by # str | Specifies the order by criteria. The syntax of this parameter is similar to the syntax of the `order by` clause of an SQL statement. Each query can be ordered by any of the following `kafkaRequests` fields:  * bootstrap_server_host * admin_api_server_url * cloud_provider * cluster_id * created_at * href * id * instance_type * multi_az * name * organisation_id * owner * reauthentication_enabled * region * status * updated_at * version  For example, to return all Kafka instances ordered by their name, use the following syntax:  ```sql name asc ```  To return all Kafka instances ordered by their name _and_ created date, use the following syntax:  ```sql name asc, created_at asc ```  If the parameter isn't provided, or if the value is empty, then the results are ordered by name. (optional)
    search = args.search # str | Search criteria.  The syntax of this parameter is similar to the syntax of the `where` clause of an SQL statement. Allowed fields in the search are `cloud_provider`, `name`, `owner`, `region`, and `status`. Allowed comparators are `<>`, `=`, `LIKE`, or `ILIKE`. Allowed joins are `AND` and `OR`. However, you can use a maximum of 10 joins in a search query.  Examples:  To return a Kafka instance with the name `my-kafka` and the region `aws`, use the following syntax:  ``` name = my-kafka and cloud_provider = aws ```[p-]  To return a Kafka instance with a name that starts with `my`, use the following syntax:  ``` name like my%25 ```  To return a Kafka instance with a name containing `test` matching any character case combinations, use the following syntax:  ``` name ilike %25test%25 ```  If the parameter isn't provided, or if the value is empty, then all the Kafka instances that the user has permission to see are returned.  Note. If the query is invalid, an error is returned.  (optional)

    try:
        api_response = api_instance.get_kafkas(page=page, size=size, order_by=order_by, search=search)
        api_response = clean_datetime_bool(api_response.to_dict())

        pprint(api_response)
    except rhoas_kafka_mgmt_sdk.ApiException as e:
        print("Exception when calling DefaultApi->get_kafkas: %s\n" % e)