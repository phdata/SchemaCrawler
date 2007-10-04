/* 
 *
 * SchemaCrawler
 * http://sourceforge.net/projects/schemacrawler
 * Copyright (c) 2000-2007, Sualeh Fatehi.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 */

package schemacrawler.main;


import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import schemacrawler.crawl.InformationSchemaViews;
import schemacrawler.tools.ExecutionContext;
import schemacrawler.tools.Executor;
import schemacrawler.tools.ToolsExecutor;
import sf.util.Config;
import dbconnector.Version;
import dbconnector.datasource.PropertiesDataSource;
import dbconnector.dbconnector.DatabaseConnector;
import dbconnector.dbconnector.DatabaseConnectorFactory;

/**
 * Main class that takes arguments for a database for crawling a schema.
 */
public final class SchemaCrawlerMain
{

  private static final Logger LOGGER = Logger.getLogger(SchemaCrawlerMain.class
    .getName());

  /**
   * Executes with the command line, and a given executor. The executor
   * allows for the command line to be parsed independently of the
   * execution. The execution can integrate with other software, such as
   * Velocity.
   * 
   * @param args
   *        Command line arguments
   * @param executor
   *        Executor
   * @throws Exception
   *         On an exception
   */
  public static void schemacrawler(final String[] args, final Executor executor)
    throws Exception
  {
    final Config config = ConfigParser.parseCommandLine(args);
    final DatabaseConnector dataSourceParser = DatabaseConnectorFactory
      .createPropertiesDriverDataSourceParser(args, config);
    schemacrawler(args, config, executor, dataSourceParser);
  }

  /**
   * Executes with the command line, and a given executor. The executor
   * allows for the command line to be parsed independently of the
   * execution. The execution can integrate with other software, such as
   * Velocity.
   * 
   * @param args
   *        Command line arguments
   * @param config
   *        Configuration
   * @param executor
   *        Executor
   * @param dataSourceParser
   *        Datasource parser
   * @throws Exception
   *         On an exception
   */
  public static void schemacrawler(final String[] args,
                                   final Config config,
                                   final Executor executor,
                                   final DatabaseConnector dataSourceParser)
    throws Exception
  {

    final ExecutionContext[] executionContexts = ExecutionContextFactory
      .createExecutionContexts(args, config);
    if (executionContexts.length > 0)
    {
      LOGGER.log(Level.CONFIG, Version.about());
      LOGGER.log(Level.CONFIG, "Commandline: " + Arrays.asList(args));
      for (final ExecutionContext executionContext: executionContexts)
      {
        LOGGER.log(Level.CONFIG, executionContext.toString());
        final DataSource dataSource = dataSourceParser.createDataSource();
        if (executor instanceof ToolsExecutor)
        {
          ((ToolsExecutor) executor)
            .setInformationSchemaViews(new InformationSchemaViews(((PropertiesDataSource) dataSource)
              .getSourceConfiguration()));
        }
        executor.execute(executionContext, dataSource);
      }
    }
  }

  private SchemaCrawlerMain()
  {
    // Prevent instantiation
  }

}
