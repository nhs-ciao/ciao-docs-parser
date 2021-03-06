package uk.nhs.ciao.docs.parser;

import static uk.nhs.ciao.logging.CiaoLogMessage.logMsg;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import uk.nhs.ciao.docs.parser.DocumentParser;
import uk.nhs.ciao.docs.parser.StandardProperties.Metadata;
import uk.nhs.ciao.logging.CiaoLogger;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.Closeables;

/**
 * A camel processor to parse an incoming document.
 * <p>
 * The processor delegates parsing to the {@link DocumentParser} provided
 * at runtime.
 */
public class DocumentParserProcessor implements Processor {
	private static final CiaoLogger LOGGER = CiaoLogger.getLogger(DocumentParserProcessor.class);
	
	private final DocumentParser parser;
	
	/***
	 * Constructs a new processor backed by the specified document parser
	 * 
	 * @param parser The parser used to process incoming documents
	 */
	public DocumentParserProcessor(final DocumentParser parser) {
		this.parser = Preconditions.checkNotNull(parser);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Delegates processing of the incoming document to the configured parser
	 * and adds the extracted properties to the exchange output message as
	 * {@link ParsedDocument}.
	 * 
	 * @throws UnsupportedDocumentTypeException If the parser does not support the type (e.g. syntax)
	 * 		of the incoming document
	 * @throws IOException If parser failed to read the incoming document
	 */
	@Override
	public void process(final Exchange exchange) throws UnsupportedDocumentTypeException, IOException {
		LOGGER.debug(logMsg("process"));
		
		final Document originalDocument = toDocument(exchange.getIn());		
		final InputStream inputStream = originalDocument.getContentStream();
		
		try {
			LOGGER.info(logMsg("Attempting to parse document properties")
					.originalFileName(originalDocument.getName()));
			
			final Map<String, Object> properties = parser.parseDocument(inputStream);

			LOGGER.debug(logMsg("Parsed document properties")
					.originalFileName(originalDocument.getName())
					.documentProperties(properties));
			
			setOriginalDocumentMediaType(originalDocument, properties);
			
			final Message outputMessage = exchange.getOut();
			final ParsedDocument parsedDocument = new ParsedDocument(originalDocument, properties);
			outputMessage.copyFrom(exchange.getIn());			
			outputMessage.setBody(parsedDocument);
			outputMessage.setHeader(Exchange.FILE_NAME, originalDocument.getName());
		} finally {
			Closeables.closeQuietly(inputStream);
		}
	}
	
	/**
	 * Returns a document instance corresponding to the specified Camel message
	 * <p>
	 * The camel FILE_NAME header is used as the document name
	 * 
	 * @param message The camel message representation of the document
	 * @return The associated document instance
	 */
	public static Document toDocument(final Message message) {
		final String name = message.getHeader(Exchange.FILE_NAME, String.class);
		final byte[] body = message.getBody(byte[].class);
		
		final Document document = new Document(name, body);
		
		final String mediaType = message.getHeader(Exchange.CONTENT_TYPE, String.class);
		if (!Strings.isNullOrEmpty(mediaType)) {
			document.setMediaType(mediaType);
		}
		
		return document;
	}
	
	/**
	 * Updates the media type of the original document (if the parser has detected one)
	 */
	private void setOriginalDocumentMediaType(final Document originalDocument, final Map<String, Object> properties) {
		if (properties == null) {
			return;
		}
		
		final Metadata metadata = new StandardProperties(properties).getMetadata();
		final String mediaType = metadata.getContentType();
		if (!Strings.isNullOrEmpty(mediaType)) {
			originalDocument.setMediaType(mediaType);
		}
	}
}
