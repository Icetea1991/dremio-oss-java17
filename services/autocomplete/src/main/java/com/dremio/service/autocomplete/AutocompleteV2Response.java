/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.service.autocomplete;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "entityType", visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ColumnSuggestions.class, name = "columnSuggestions"),
  @JsonSubTypes.Type(value = ContainerSuggestions.class, name = "containerSuggestions"),
  @JsonSubTypes.Type(value = ReferenceSuggestions.class, name = "referenceSuggestions")
})
public interface AutocompleteV2Response {
  String getSuggestionsType();
  Integer getCount();
  Integer getMaxCount();
}
