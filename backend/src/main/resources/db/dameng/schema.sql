create table extract_config (
  id varchar(64) primary key,
  config_code varchar(128) not null,
  config_name varchar(200) not null,
  category varchar(100),
  sub_category varchar(100),
  template_type varchar(160),
  document_type varchar(100) not null,
  department_id varchar(100) not null,
  owner_role varchar(100),
  default_priority varchar(20),
  status varchar(30) not null,
  version int not null,
  config_payload clob,
  created_by varchar(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_extract_config_code_version unique (config_code, version)
);

create index idx_extract_config_status on extract_config(status);
create index idx_extract_config_department on extract_config(department_id);
create index idx_extract_config_document_type on extract_config(document_type);

create table parse_config (
  id varchar(64) primary key,
  extract_config_id varchar(64),
  engine_code varchar(100),
  output_format varchar(50),
  preprocess_enabled char(1) default '1',
  engine_params clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table parse_preprocess_step_config (
  id varchar(64) primary key,
  parse_config_id varchar(64),
  step_type varchar(50) not null,
  step_name varchar(100),
  sort_no int,
  enabled char(1) default '1',
  page_ranges varchar(500),
  include_keywords clob,
  exclude_keywords clob,
  image_quality varchar(50),
  image_dpi int,
  image_format varchar(20),
  split_page_count int,
  merge_order varchar(50),
  config_json clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table result_table_config (
  id varchar(64) primary key,
  table_code varchar(128) not null,
  table_name varchar(200) not null,
  table_comment varchar(1000),
  owner_department_id varchar(100),
  storage_datasource varchar(100),
  auto_create_table char(1) default '0',
  auto_add_column char(1) default '0',
  ddl_status varchar(30),
  status varchar(30) not null,
  created_by varchar(100),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_table_code unique (table_code)
);

create table result_table_column_config (
  id varchar(64) primary key,
  result_table_id varchar(64) not null,
  column_name varchar(128) not null,
  column_name_cn varchar(200),
  db_type varchar(50) not null,
  type_params varchar(50),
  field_length int,
  field_precision int,
  field_scale int,
  required char(1) default '0',
  default_value varchar(500),
  validation_rule varchar(500),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_column unique (result_table_id, column_name)
);

create table storage_mapping_profile (
  id varchar(64) primary key,
  profile_code varchar(128) not null,
  profile_name varchar(200) not null,
  extract_config_id varchar(64) not null,
  result_table_id varchar(64) not null,
  save_mode varchar(30),
  primary_key_strategy varchar(100),
  status varchar(30) not null,
  version int not null,
  description varchar(1000),
  created_by varchar(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_field_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  field_code varchar(128) not null,
  field_name varchar(200) not null,
  field_desc varchar(1000),
  extract_required char(1) default '0',
  multiple char(1) default '0',
  sort_no int,
  extract_methods varchar(200),
  strategy_config clob,
  prompt_hint varchar(1000),
  enabled char(1) default '1',
  constraint uk_extract_field unique (extract_config_id, field_code)
);

create table storage_field_mapping_config (
  id varchar(64) primary key,
  mapping_profile_id varchar(64) not null,
  extract_field_config_id varchar(64),
  extract_field_code varchar(128) not null,
  target_column_config_id varchar(64),
  target_column varchar(128) not null,
  multiple char(1) default '0',
  transform_rule_id varchar(64),
  required_for_storage char(1) default '0',
  sort_no int,
  enabled char(1) default '1',
  remark varchar(1000)
);

create table storage_unique_constraint_config (
  id varchar(64) primary key,
  mapping_profile_id varchar(64) not null,
  result_table_id varchar(64) not null,
  constraint_name varchar(128) not null,
  unique_columns clob not null,
  duplicate_scope varchar(50),
  duplicate_strategy varchar(50),
  generate_db_index char(1) default '0',
  sort_no int,
  enabled char(1) default '1',
  description varchar(1000),
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_regex_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  extract_field_config_id varchar(64),
  field_code varchar(128) not null,
  rule_name varchar(200),
  regex_pattern clob not null,
  regex_group int,
  regex_flags varchar(50),
  sample_text clob,
  sample_result varchar(1000),
  validation_status varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  sort_no int,
  input_field varchar(128),
  output_field varchar(128),
  output_mode varchar(50),
  condition_enabled char(1) default '0',
  condition_field varchar(128),
  condition_operator varchar(50),
  condition_value varchar(500),
  on_fail varchar(50),
  rule_config clob,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_dict_item_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  match_mode varchar(50),
  source_value varchar(500),
  target_value varchar(500),
  sort_no int,
  enabled char(1) default '1'
);

create table transform_api_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  downstream_service_id varchar(64),
  http_method varchar(20),
  endpoint_override varchar(500),
  request_param_mapping clob,
  response_value_path varchar(500),
  success_rule varchar(500),
  timeout_seconds int,
  retry_count int,
  auth_mode varchar(50)
);

create table transform_sql_param_config (
  id varchar(64) primary key,
  transform_rule_id varchar(64) not null,
  datasource_code varchar(100),
  sql_template clob,
  request_param_mapping clob,
  result_column varchar(128),
  max_rows int,
  readonly_checked char(1) default '0'
);

create table validation_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  rule_code varchar(128),
  rule_name varchar(200) not null,
  rule_type varchar(50) not null,
  field_code varchar(128),
  expression clob,
  severity varchar(30),
  fail_message varchar(1000),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  confidence_threshold decimal(5,4),
  reviewer_role_id varchar(100),
  review_assign_mode varchar(50),
  on_validation_warn varchar(50),
  on_validation_block varchar(50),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_field_config (
  id varchar(64) primary key,
  review_policy_id varchar(64) not null,
  field_code varchar(128) not null,
  force_review char(1) default '0',
  field_confidence_threshold decimal(5,4),
  review_reason varchar(500),
  sort_no int
);

create table downstream_system_config (
  id varchar(64) primary key,
  system_code varchar(128) not null,
  system_name varchar(200) not null,
  owner_department_id varchar(100),
  default_auth_mode varchar(50),
  default_timeout_seconds int,
  default_retry_count int,
  status varchar(30),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_system_code unique (system_code)
);

create table downstream_service_config (
  id varchar(64) primary key,
  system_id varchar(64) not null,
  service_code varchar(128) not null,
  service_name varchar(200) not null,
  purpose varchar(100),
  service_type varchar(50),
  endpoint varchar(500),
  http_method varchar(20),
  auth_mode varchar(50),
  timeout_seconds int,
  retry_count int,
  response_success_rule varchar(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_service_code unique (service_code)
);

create table config_push_rule_config (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  downstream_service_id varchar(64) not null,
  push_enabled char(1) default '1',
  push_trigger varchar(50),
  push_scope varchar(50),
  push_mode varchar(30),
  idempotent_key_expr varchar(500),
  fail_strategy varchar(50),
  sort_no int,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table config_push_field_mapping (
  id varchar(64) primary key,
  push_rule_id varchar(64) not null,
  source_field varchar(128),
  downstream_field varchar(128),
  required char(1) default '0',
  default_value varchar(500),
  transform_expr varchar(1000),
  sort_no int,
  enabled char(1) default '1'
);

create table config_permission_scope (
  id varchar(64) primary key,
  extract_config_id varchar(64) not null,
  department_id varchar(100),
  role_id varchar(100),
  permission_type varchar(50),
  data_scope varchar(50),
  custom_scope_expr varchar(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table validation_test_record (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64) not null,
  config_version int,
  sample_document_id varchar(64),
  status varchar(30),
  parse_result_path varchar(500),
  extract_result_json clob,
  validation_result_json clob,
  storage_preview_json clob,
  error_message varchar(1000),
  created_by varchar(100),
  created_at timestamp not null
);

create table validation_test_stage_log (
  id varchar(64) primary key,
  test_id varchar(64) not null,
  extract_config_id varchar(64),
  stage varchar(50),
  status varchar(30),
  input_summary varchar(1000),
  output_summary varchar(1000),
  error_message varchar(1000),
  started_at timestamp,
  ended_at timestamp,
  duration_ms bigint,
  expire_at timestamp,
  cleaned char(1) default '0'
);
