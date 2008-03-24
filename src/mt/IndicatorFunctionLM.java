package mt;

/**
 * 
 * @author danielcer
 *
 */
public class IndicatorFunctionLM implements LanguageModel<IString> {
	public static final String NAME = "IndicatorFunctionLM";
	public static final IString START_TOKEN = new IString("<s>");
	public static final IString END_TOKEN = new IString("</s>");

	final int order;
	
	/**
	 * 
	 * @param <FV>
	 * @param order
	 */
	public <FV> IndicatorFunctionLM(int order) {
		this.order = order;
	}
	
	@Override
	public IString getEndToken() {
		return END_TOKEN;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public IString getStartToken() {
		return START_TOKEN;
	}

	@Override
	public int order() {
		return order;
	}

	@Override
	public boolean releventPrefix(Sequence<IString> sequence) {
		// TODO: make this weight vector aware so we don't always need to return 'true'
		// when sequence.size() <= order -1;
		return sequence.size() <= order -1;
	}

	@Override
	public double score(Sequence<IString> sequence) {
		return 1.0;
	}

}
