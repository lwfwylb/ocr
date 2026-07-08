create table extract_config (
  id varchar2(64) primary key,
  config_code varchar2(128) not null,
  config_name varchar2(200) not null,
  category varchar2(100),
  sub_category varchar2(100),
  template_type varchar2(160),
  document_type varchar2(100) not null,
  department_id varchar2(100) not null,
  owner_role varchar2(100),
  default_priority varchar2(20),
  status varchar2(30) not null,
  version number(10) not null,
  config_payload clob,
  created_by varchar2(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_extract_config_code_version unique (config_code, version)
);

create index idx_extract_config_status on extract_config(status);
create index idx_extract_config_department on extract_config(department_id);
create index idx_extract_config_document_type on extract_config(document_type);

create table parse_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64),
  engine_code varchar2(100),
  output_format varchar2(50),
  preprocess_enabled char(1) default '1',
  engine_params clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table parse_preprocess_step_config (
  id varchar2(64) primary key,
  parse_config_id varchar2(64),
  step_type varchar2(50) not null,
  step_name varchar2(100),
  sort_no number(10),
  enabled char(1) default '1',
  page_ranges varchar2(500),
  include_keywords clob,
  exclude_keywords clob,
  image_quality varchar2(50),
  image_dpi number(10),
  image_format varchar2(20),
  split_page_count number(10),
  merge_order varchar2(50),
  config_json clob,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table result_table_config (
  id varchar2(64) primary key,
  table_code varchar2(128) not null,
  table_name varchar2(200) not null,
  table_comment varchar2(1000),
  owner_department_id varchar2(100),
  storage_datasource varchar2(100),
  auto_create_table char(1) default '0',
  auto_add_column char(1) default '0',
  ddl_status varchar2(30),
  status varchar2(30) not null,
  created_by varchar2(100),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_table_code unique (table_code)
);

create table result_table_column_config (
  id varchar2(64) primary key,
  result_table_id varchar2(64) not null,
  column_name varchar2(128) not null,
  column_name_cn varchar2(200),
  db_type varchar2(50) not null,
  type_params varchar2(50),
  field_length number(10),
  field_precision number(10),
  field_scale number(10),
  required char(1) default '0',
  default_value varchar2(500),
  validation_rule varchar2(500),
  sort_no number(10),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_result_column unique (result_table_id, column_name)
);

create table storage_mapping_profile (
  id varchar2(64) primary key,
  profile_code varchar2(128) not null,
  profile_name varchar2(200) not null,
  extract_config_id varchar2(64) not null,
  result_table_id varchar2(64) not null,
  save_mode varchar2(30),
  primary_key_strategy varchar2(100),
  status varchar2(30) not null,
  version number(10) not null,
  description varchar2(1000),
  created_by varchar2(100),
  published_at timestamp null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_field_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  field_code varchar2(128) not null,
  field_name varchar2(200) not null,
  field_desc varchar2(1000),
  extract_required char(1) default '0',
  multiple char(1) default '0',
  sort_no number(10),
  extract_methods varchar2(200),
  strategy_config clob,
  prompt_hint varchar2(1000),
  enabled char(1) default '1',
  constraint uk_extract_field unique (extract_config_id, field_code)
);

create table storage_field_mapping_config (
  id varchar2(64) primary key,
  mapping_profile_id varchar2(64) not null,
  extract_field_config_id varchar2(64),
  extract_field_code varchar2(128) not null,
  target_column_config_id varchar2(64),
  target_column varchar2(128) not null,
  multiple char(1) default '0',
  transform_rule_id varchar2(64),
  required_for_storage char(1) default '0',
  sort_no number(10),
  enabled char(1) default '1',
  remark varchar2(1000)
);

create table storage_unique_constraint_config (
  id varchar2(64) primary key,
  mapping_profile_id varchar2(64) not null,
  result_table_id varchar2(64) not null,
  constraint_name varchar2(128) not null,
  unique_columns clob not null,
  duplicate_scope varchar2(50),
  duplicate_strategy varchar2(50),
  generate_db_index char(1) default '0',
  sort_no number(10),
  enabled char(1) default '1',
  description varchar2(1000),
  created_at timestamp not null,
  updated_at timestamp not null
);

create table extract_regex_rule_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  extract_field_config_id varchar2(64),
  field_code varchar2(128) not null,
  rule_name varchar2(200),
  regex_pattern clob not null,
  regex_group number(10),
  regex_flags varchar2(50),
  sample_text clob,
  sample_result varchar2(1000),
  validation_status varchar2(30),
  fail_message varchar2(1000),
  sort_no number(10),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_rule_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  rule_code varchar2(128),
  rule_name varchar2(200) not null,
  rule_type varchar2(50) not null,
  sort_no number(10),
  input_field varchar2(128),
  output_field varchar2(128),
  output_mode varchar2(50),
  condition_enabled char(1) default '0',
  condition_field varchar2(128),
  condition_operator varchar2(50),
  condition_value varchar2(500),
  on_fail varchar2(50),
  rule_config clob,
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table transform_dict_item_config (
  id varchar2(64) primary key,
  transform_rule_id varchar2(64) not null,
  match_mode varchar2(50),
  source_value varchar2(500),
  target_value varchar2(500),
  sort_no number(10),
  enabled char(1) default '1'
);

create table transform_api_param_config (
  id varchar2(64) primary key,
  transform_rule_id varchar2(64) not null,
  downstream_service_id varchar2(64),
  http_method varchar2(20),
  endpoint_override varchar2(500),
  request_param_mapping clob,
  response_value_path varchar2(500),
  success_rule varchar2(500),
  timeout_seconds number(10),
  retry_count number(10),
  auth_mode varchar2(50)
);

create table transform_sql_param_config (
  id varchar2(64) primary key,
  transform_rule_id varchar2(64) not null,
  datasource_code varchar2(100),
  sql_template clob,
  request_param_mapping clob,
  result_column varchar2(128),
  max_rows number(10),
  readonly_checked char(1) default '0'
);

create table validation_rule_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  rule_code varchar2(128),
  rule_name varchar2(200) not null,
  rule_type varchar2(50) not null,
  field_code varchar2(128),
  expression clob,
  severity varchar2(30),
  fail_message varchar2(1000),
  sort_no number(10),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  confidence_threshold number(5,4),
  reviewer_role_id varchar2(100),
  review_assign_mode varchar2(50),
  on_validation_warn varchar2(50),
  on_validation_block varchar2(50),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table review_policy_field_config (
  id varchar2(64) primary key,
  review_policy_id varchar2(64) not null,
  field_code varchar2(128) not null,
  force_review char(1) default '0',
  field_confidence_threshold number(5,4),
  review_reason varchar2(500),
  sort_no number(10)
);

create table downstream_system_config (
  id varchar2(64) primary key,
  system_code varchar2(128) not null,
  system_name varchar2(200) not null,
  owner_department_id varchar2(100),
  default_auth_mode varchar2(50),
  default_timeout_seconds number(10),
  default_retry_count number(10),
  status varchar2(30),
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_system_code unique (system_code)
);

create table downstream_service_config (
  id varchar2(64) primary key,
  system_id varchar2(64) not null,
  service_code varchar2(128) not null,
  service_name varchar2(200) not null,
  purpose varchar2(100),
  service_type varchar2(50),
  endpoint varchar2(500),
  http_method varchar2(20),
  auth_mode varchar2(50),
  timeout_seconds number(10),
  retry_count number(10),
  response_success_rule varchar2(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_downstream_service_code unique (service_code)
);

create table config_push_rule_config (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  downstream_service_id varchar2(64) not null,
  push_enabled char(1) default '1',
  push_trigger varchar2(50),
  push_scope varchar2(50),
  push_mode varchar2(30),
  idempotent_key_expr varchar2(500),
  fail_strategy varchar2(50),
  sort_no number(10),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table config_push_field_mapping (
  id varchar2(64) primary key,
  push_rule_id varchar2(64) not null,
  source_field varchar2(128),
  downstream_field varchar2(128),
  required char(1) default '0',
  default_value varchar2(500),
  transform_expr varchar2(1000),
  sort_no number(10),
  enabled char(1) default '1'
);

create table config_permission_scope (
  id varchar2(64) primary key,
  extract_config_id varchar2(64) not null,
  department_id varchar2(100),
  role_id varchar2(100),
  permission_type varchar2(50),
  data_scope varchar2(50),
  custom_scope_expr varchar2(1000),
  enabled char(1) default '1',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table validation_test_record (
  id varchar2(64) primary key,
  test_id varchar2(64) not null,
  extract_config_id varchar2(64) not null,
  config_version number(10),
  sample_document_id varchar2(64),
  status varchar2(30),
  parse_result_path varchar2(500),
  extract_result_json clob,
  validation_result_json clob,
  storage_preview_json clob,
  error_message varchar2(1000),
  created_by varchar2(100),
  created_at timestamp not null
);

create table validation_test_stage_log (
  id varchar2(64) primary key,
  test_id varchar2(64) not null,
  extract_config_id varchar2(64),
  stage varchar2(50),
  status varchar2(30),
  input_summary varchar2(1000),
  output_summary varchar2(1000),
  error_message varchar2(1000),
  started_at timestamp,
  ended_at timestamp,
  duration_ms number(18),
  expire_at timestamp,
  cleaned char(1) default '0'
);

create table llm_model_config (
  id varchar2(64) primary key,
  model_code varchar2(128) not null,
  model_name varchar2(200) not null,
  provider varchar2(100) not null,
  base_url varchar2(500) not null,
  api_key_secret_ref varchar2(200),
  model_identifier varchar2(200) not null,
  temperature number(5,3) default 0.100,
  max_tokens number(10) default 4096,
  timeout_seconds number(10) default 120,
  retry_count number(10) default 1,
  json_schema_required char(1) default '1',
  default_model char(1) default '0',
  status varchar2(30) not null,
  description varchar2(1000),
  created_by varchar2(100),
  created_at timestamp not null,
  updated_at timestamp not null
);

create unique index uk_llm_model_code on llm_model_config (model_code);
create index idx_llm_model_status on llm_model_config (status);
create index idx_llm_model_default on llm_model_config (default_model);
