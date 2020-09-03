/*
========================================================================
SchemaCrawler
http://www.schemacrawler.com
Copyright (c) 2000-2020, Sualeh Fatehi <sualeh@hotmail.com>.
All rights reserved.
------------------------------------------------------------------------

SchemaCrawler is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

SchemaCrawler and the accompanying materials are made available under
the terms of the Eclipse Public License v1.0, GNU General Public License
v3 or GNU Lesser General Public License v3.

You may elect to redistribute this code under any of these licenses.

The Eclipse Public License is available at:
http://www.eclipse.org/legal/epl-v10.html

The GNU General Public License v3 and the GNU Lesser General Public
License v3 are available at:
http://www.gnu.org/licenses/

========================================================================
*/

package schemacrawler.crawl;


import static java.util.Objects.requireNonNull;
import static schemacrawler.schemacrawler.InformationSchemaKey.TYPE_INFO;
import static schemacrawler.schemacrawler.SchemaInfoMetadataRetrievalStrategy.typeInfoRetrievalStrategy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.logging.Level;

import schemacrawler.SchemaCrawlerLogger;
import schemacrawler.schema.ColumnDataType;
import schemacrawler.schema.Schema;
import schemacrawler.schema.SearchableType;
import schemacrawler.schemacrawler.InformationSchemaViews;
import schemacrawler.schemacrawler.Query;
import schemacrawler.schemacrawler.SchemaCrawlerOptions;
import schemacrawler.schemacrawler.SchemaCrawlerSQLException;
import schemacrawler.schemacrawler.SchemaReference;
import us.fatehi.utility.string.StringFormat;

final class DataTypeRetriever
  extends AbstractRetriever
{

  private static final SchemaCrawlerLogger LOGGER =
    SchemaCrawlerLogger.getLogger(DataTypeRetriever.class.getName());

  DataTypeRetriever(final RetrieverConnection retrieverConnection,
                    final MutableCatalog catalog,
                    final SchemaCrawlerOptions options)
    throws SQLException
  {
    super(retrieverConnection, catalog, options);
  }

  /**
   * Retrieves column data type metadata.
   *
   * @throws SQLException
   *   On a SQL exception
   */
  void retrieveSystemColumnDataTypes()
    throws SQLException
  {
    final Schema systemSchema = new SchemaReference();

    switch (getRetrieverConnection().get(typeInfoRetrievalStrategy))
    {
      case data_dictionary_all:
        LOGGER.log(Level.INFO,
                   "Retrieving system column data types, using fast data dictionary retrieval");
        retrieveSystemColumnDataTypesFromDataDictionary(systemSchema);
        break;

      case metadata:
        LOGGER.log(Level.INFO, "Retrieving system column data types");
        retrieveSystemColumnDataTypesFromMetadata(systemSchema);
        break;

      default:
        break;
    }
  }

  void retrieveUserDefinedColumnDataTypes(final Schema schema)
    throws SQLException
  {
    requireNonNull(schema, "No schema provided");

    final Optional<SchemaReference> schemaOptional =
      catalog.lookupSchema(schema.getFullName());
    if (!schemaOptional.isPresent())
    {
      LOGGER.log(Level.INFO,
                 new StringFormat(
                   "Cannot locate schema, so not retrieving data types for schema: %s",
                   schema));
      return;
    }

    LOGGER.log(Level.INFO,
               new StringFormat("Retrieving data types for schema <%s>",
                                schema));

    final String catalogName = schema.getCatalogName();
    final String schemaName = schema.getName();

    try (
      final MetadataResultSet results = new MetadataResultSet(getMetaData().getUDTs(
        catalogName,
        schemaName,
        null,
        null))
    )
    {
      while (results.next())
      {
        // "TYPE_CAT", "TYPE_SCHEM"
        final String typeName = results.getString("TYPE_NAME");
        LOGGER.log(Level.FINE,
                   new StringFormat("Retrieving data type <%s.%s>",
                                    schema,
                                    typeName));
        final int dataType = results.getInt("DATA_TYPE", 0);
        final String className = results.getString("CLASS_NAME");
        final String remarks = results.getString("REMARKS");
        final short baseTypeValue = results.getShort("BASE_TYPE", (short) 0);

        final ColumnDataType baseType;
        if (baseTypeValue != 0)
        {
          baseType = catalog.lookupBaseColumnDataTypeByType(baseTypeValue);
        }
        else
        {
          baseType = null;
        }
        final MutableColumnDataType columnDataType =
          lookupOrCreateColumnDataType(schema, dataType, typeName, className);
        columnDataType.setUserDefined(true);
        columnDataType.setBaseType(baseType);
        columnDataType.setRemarks(remarks);

        columnDataType.addAttributes(results.getAttributes());

        catalog.addColumnDataType(columnDataType);
      }
    }

  }

  private void retrieveSystemColumnDataTypesFromDataDictionary(final Schema systemSchema)
    throws SQLException
  {
    final InformationSchemaViews informationSchemaViews =
      getRetrieverConnection().getInformationSchemaViews();
    if (!informationSchemaViews.hasQuery(TYPE_INFO))
    {
      throw new SchemaCrawlerSQLException(
        "No system column data types SQL provided",
        null);
    }
    final Query typeInfoSql = informationSchemaViews.getQuery(TYPE_INFO);
    final Connection connection = getDatabaseConnection();

    try (
      final Statement statement = connection.createStatement();
      final MetadataResultSet results = new MetadataResultSet(typeInfoSql,
                                                              statement,
                                                              getSchemaInclusionRule())
    )
    {
      results.setDescription("retrieveSystemColumnDataTypesFromDataDictionary");
      int numSystemColumnDataTypes = 0;
      while (results.next())
      {
        numSystemColumnDataTypes = numSystemColumnDataTypes + 1;
        createSystemColumnDataType(results, systemSchema);
      }
      LOGGER.log(Level.INFO,
                 new StringFormat("Processed %d system column data types",
                                  numSystemColumnDataTypes));
    }
  }

  private void retrieveSystemColumnDataTypesFromMetadata(final Schema systemSchema)
    throws SQLException
  {
    try (final MetadataResultSet results = new MetadataResultSet(getMetaData().getTypeInfo()))
    {
      results.setDescription("retrieveSystemColumnDataTypesFromDataDictionary");
      int numSystemColumnDataTypes = 0;
      while (results.next())
      {
        numSystemColumnDataTypes = numSystemColumnDataTypes + 1;
        createSystemColumnDataType(results, systemSchema);
      }
      LOGGER.log(Level.INFO,
                 new StringFormat("Processed %d system column data types",
                                  numSystemColumnDataTypes));
    }
  }

  private void createSystemColumnDataType(final MetadataResultSet results,
                                          final Schema systemSchema)
  {
    final String typeName = results.getString("TYPE_NAME");
    final int dataType = results.getInt("DATA_TYPE", 0);
    LOGGER.log(Level.FINER,
               new StringFormat("Retrieving data type <%s> with type id %d",
                                typeName,
                                dataType));

    final long precision = results.getLong("PRECISION", 0L);
    final String literalPrefix = results.getString("LITERAL_PREFIX");
    final String literalSuffix = results.getString("LITERAL_SUFFIX");
    final String createParameters = results.getString("CREATE_PARAMS");
    final boolean isNullable =
      results.getInt("NULLABLE", DatabaseMetaData.typeNullableUnknown)
      == DatabaseMetaData.typeNullable;
    final boolean isCaseSensitive = results.getBoolean("CASE_SENSITIVE");
    final SearchableType searchable =
      results.getEnumFromId("SEARCHABLE", SearchableType.unknown);
    final boolean isUnsigned = results.getBoolean("UNSIGNED_ATTRIBUTE");
    final boolean isFixedPrecisionScale =
      results.getBoolean("FIXED_PREC_SCALE");
    final boolean isAutoIncremented = results.getBoolean("AUTO_INCREMENT");
    final String localTypeName = results.getString("LOCAL_TYPE_NAME");
    final int minimumScale = results.getInt("MINIMUM_SCALE", 0);
    final int maximumScale = results.getInt("MAXIMUM_SCALE", 0);
    final int numPrecisionRadix = results.getInt("NUM_PREC_RADIX", 0);

    final MutableColumnDataType columnDataType =
      lookupOrCreateColumnDataType(systemSchema, dataType, typeName);
    // Set the Java SQL type code, but no mapped Java class is
    // available, so use the defaults
    columnDataType.setPrecision(precision);
    columnDataType.setLiteralPrefix(literalPrefix);
    columnDataType.setLiteralSuffix(literalSuffix);
    columnDataType.setCreateParameters(createParameters);
    columnDataType.setNullable(isNullable);
    columnDataType.setCaseSensitive(isCaseSensitive);
    columnDataType.setSearchable(searchable);
    columnDataType.setUnsigned(isUnsigned);
    columnDataType.setFixedPrecisionScale(isFixedPrecisionScale);
    columnDataType.setAutoIncrementable(isAutoIncremented);
    columnDataType.setLocalTypeName(localTypeName);
    columnDataType.setMinimumScale(minimumScale);
    columnDataType.setMaximumScale(maximumScale);
    columnDataType.setNumPrecisionRadix(numPrecisionRadix);

    columnDataType.addAttributes(results.getAttributes());

    catalog.addColumnDataType(columnDataType);
  }

}