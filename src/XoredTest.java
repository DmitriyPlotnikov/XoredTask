/**Решение тестового задания "Маленький Эксель" от Xored
 * Автор: Плотников Дмитрий 
 * 
 */

import java.io.*;
import java.util.*;
import java.math.BigInteger;

/**Реализует простую электронную таблицу в виде программы, выполняющейся из командной строки.*/
public class XoredTest {

	public static void main(String[] args) {
		try {
			LittleExcel excel = new LittleExcel();
			/*String input = "4	4\n"
					+ "12	=C2	3	'Sample\n"
					+ "=A1+B1*C1/5	=A2*B1	=B3-C3	'Spread\n"
					+ "'Test	=4-3	5	'Sheet\n"
					+ "=B2	=a4	>asd23";
			excel.load(new ByteArrayInputStream(input.getBytes()));*/
			excel.load(System.in);
			excel.print(System.out);
		} catch (XoredTestException e) {
			System.err.println("Ошибка приложения: "+e.getLocalizedMessage());
		} catch (IOException e) {
			System.err.println("Ошибка ввода-вывода: "+e.getLocalizedMessage());
		} catch (Exception e) {
			System.err.println("Неизвестная ошибка: "+e.getLocalizedMessage());
		}
	}
	
}
//*************Грамматика допустимых выражений*************
	/**Описывает подвыражения, из которых может состоять строка-выражение*/
	abstract class SubExpression {}

	/**Описывает терм, т.е. один из двух видов подвыражений.
	 * В свою очередь терм может быть ссылкой на ячейку или числом*/
	abstract class Term extends SubExpression {
		public abstract int intValue() throws RTException;
	}
	/**Описывает терм-ссылку на ячейку.
	 * Поля col и row - индексы столбца и строчки.
	 * Минимальное значение индексов: 0*/
	class CellReference extends Term {
		private byte col;
		private byte row;
		private LittleExcel tab;
		
		public CellReference(byte col, byte row, LittleExcel tab) {
			this.col = col;
			this.row = row;
			this.tab = tab;
		}
		
		public int intValue () throws RTException {
			return tab.getCell(row, col).intValue();
		}
	}
	/**Описывает терм-число*/
	class NonNegNumber extends Term {
		private int number;
		public NonNegNumber(int value) {
			number = value;
		}
		public int intValue() {
			return number;
		}
	}

	/**Описывает подвыражение-операцию*/
	abstract class Operation extends SubExpression {
		public abstract int exec(int left, int right) throws RTException;
	}
	/**Описывает операцию сложения*/
	class SumOp extends Operation {
		public int exec(int left, int right) throws RTException {
			BigInteger bigInt = BigInteger.valueOf(left);
			bigInt = bigInt.add(BigInteger.valueOf(right));
			try {
				return bigInt.intValueExact();
			} catch (ArithmeticException e) {
				throw new RTException(RTException.OVERFLOW_ERROR);
			}
		}
	}
	/**Описывает операцию вычитания*/
	class SubOp extends Operation {
		public int exec(int left, int right) throws RTException {
			BigInteger bigInt = BigInteger.valueOf(left);
			bigInt = bigInt.subtract(BigInteger.valueOf(right));
			try {
				return bigInt.intValueExact();
			} catch (ArithmeticException e) {
				throw new RTException(RTException.OVERFLOW_ERROR);
			}
		}
	}
	/**Описывает операцию умножения*/
	class MultOp extends Operation {
		public int exec(int left, int right) throws RTException {
			BigInteger bigInt = BigInteger.valueOf(left);
			bigInt = bigInt.multiply(BigInteger.valueOf(right));
			try {
				return bigInt.intValueExact();
			} catch (ArithmeticException e) {
				throw new RTException(RTException.OVERFLOW_ERROR);
			}
		}
	}
	/**Описывает операцию деления*/
	class DivOp extends Operation {
		public int exec(int left, int right) throws RTException {
			BigInteger bigInt = BigInteger.valueOf(left);
			try { 
				bigInt = bigInt.divide(BigInteger.valueOf(right));//деление может кинуть ошибку, её надо поймать 
				return bigInt.intValueExact();
			} catch (ArithmeticException e) {
				throw new RTException(RTException.DIV0_ERROR);
			}
		}
	}
//*************Конец: Грамматика допустимых выражений*************

//*************Виды ячеек***********************************
/**Описывает обобщенную ячейку*/
abstract class Cell {
	public abstract String stringValue() throws RTException;
	public abstract int intValue() throws RTException;
}
/**Пустая ячейка. Повторяет поведение пустой ячейки из MS Excel.*/
class EmptyCell extends Cell {
	public String stringValue() {
		return "";
	}
	public int intValue() {
		return 0;
	}
}
/**Числовая ячейка. При вводе не может содержать отрицательное число.*/
class NumCell extends Cell {
	private int value;
	public NumCell(int value) throws RTException {
		if (value<0) {
			throw new RTException(RTException.NEGATIVE_NUMBER);
		}
		this.value = value;
	}
	public int intValue() {
		return value;
	}
	public String stringValue() {
		return Integer.toString(value);
	}
}
/**Текстовая ячейка. Операции над строками текста запрещены.*/
class TextCell extends Cell {
	public static final char FIRST_SIMBOL = '\'';
	private String value;
	public TextCell(String value) {
		this.value = value;
	}
	public int intValue() throws RTException {
		throw new RTException(RTException.TEXT_IN_EXPRESSION);
	}
	public String stringValue() {
		return value;
	}
}
/**Ячейка с выражением.
 * 
 * <p>Поля: 
 * <br>exprStr - Строка-выражение, поданная на вход</br>
 * <br>tab - Ссылка на родительскую таблицу</br>
 * <br>expr - Список подвыражений (результат разбора exprStr)</br>
 * <br>isParsed - Признак, что строку-выражение разбирали</br>
 * <br>isChecked - Признак, что ячейку вычисляли</br>
 * <br>isEvaluated - Признак, что значение удалось вычислить</br>
 * <br>evaluatedValue - Вычисленное значение</br>
 * <br>errMessage - Строка, содержащая сообщение об ошибке разбора или вычисления выражения</br>
 * </p>
 * */
class ExprCell extends Cell {
	public static final char FIRST_SIMBOL = '=';

	private String exprStr; 
	private LittleExcel tab;
	private List<SubExpression> expr = new ArrayList<SubExpression>();
	private boolean isParsed=false;
	private boolean isChecked=false;
	private boolean isEvaluated=false;
	private int evaluatedValue;
	private String errMessage="";
	
	public ExprCell(LittleExcel tab,String value) {
		this.tab = tab;
		this.exprStr = value;
	}
	/**Разбирает полученное выражение на подвыражения, классифицируя их в соответствии с грамматикой*/
	private void parse() throws RTException {
		if (exprStr.length()==0) {
			throw new RTException(RTException.EMPTY_EXPRESSION);
		}
		StringTokenizer st = new StringTokenizer(exprStr,"+-*/",true);
		while (st.hasMoreTokens()) {
			String subExpr = st.nextToken().trim();
			SubExpression se;
			if (subExpr.length()==0) {
				throw new RTException(RTException.EMPTY_SUBEXPRESSION);
			} else if (subExpr.equals("+")) {
				se = new SumOp();
			} else if (subExpr.equals("-")) {
				se = new SubOp();
			} else if (subExpr.equals("*")) {
				se = new MultOp();
			} else if (subExpr.equals("/")) {
				se = new DivOp();
			} else {
				try {//Попытаемся прочитать как число
					se = new NonNegNumber(Integer.parseInt(subExpr));
				} catch (NumberFormatException e) {
					//если это не число, то должна быть ссылка на ячейку, проверям корректность ссылки 
					char ch = Character.toUpperCase(subExpr.charAt(0));
					if (ch<'A' || ch>'Z') {
						throw new RTException(RTException.UNKNOWN_SUBEXPRESSION);
					}
					byte ind;
					try {
						ind = Byte.parseByte(subExpr.substring(1,2));
					} catch (NumberFormatException e1) {
						throw new RTException(RTException.UNKNOWN_SUBEXPRESSION);
					}
					se = new CellReference( (byte)(Character.getNumericValue(ch)
													-Character.getNumericValue('A') )
												//Поскольку мы проверили, что первый символ лежит 
												//между A и Z, то можно смело приводить int к byte
											, (byte)(ind-1)
											, tab
										);
				}
				
			}
			expr.add(se);
		}
		//Если последним подвыражением была какая-либо операция, то это некорректное выражение
		if (expr.get(expr.size()-1) instanceof Operation) {
			throw new RTException(RTException.INCOMPLETE_SUBEXPRESSION);
		}
	}

	/**Вычисляет выражение, сохраняя вычисленное значение в	*/
	private int eval() throws RTException {
		if (!isEvaluated) {
			//Если ещё не разобрали выражение, то делаем это сейчас
			if (!isParsed) {
				try {
					parse();
				} catch (RTException e) {
					errMessage = e.getLocalizedMessage();
				} finally {
					isParsed = true;
				}
			}
			
			//В errMessage могут быть не только ошибки разбора, т.е. распарсить удалось, но при вычислении выражения возникла ошибка
			if (errMessage.length()!=0) {
				throw new RTException(errMessage);
			}

			if (isChecked) { //ещё isEvaluated==false, т.к. это условие в вышестоящем if
							//Такое сочетание флагов говорит, что уже заходили сюда, но ещё не вычислили, значит попали в цикл
				errMessage = RTException.CYCLE;
				throw new RTException(errMessage);
			}
			isChecked=true;
			
			//Начинаем вычислять выражение
			boolean isOperation = false;//в выражении первым должен стоять терм, т.е. не может стоять операция
			int res = 0;
			Operation op=null;
			
			Iterator<SubExpression> i = expr.iterator();
			while (i.hasNext()) {
				SubExpression subExp = i.next();
				if (!isOperation) {
					if (!(subExp instanceof Term)) {
						errMessage=RTException.INVALID_EXPRESSION;
						throw new RTException(errMessage);
					}
					try {
						int val = ((Term)subExp).intValue();
						res = (op==null)?val:op.exec(res, val);
						isOperation = true;//следующей должна быть операция
					} catch (RTException e) {
						errMessage = e.getLocalizedMessage() ;
						throw new RTException(errMessage);
					}
				} else {//т.е. isOperation == true
					if (!(subExp instanceof Operation)) {
						errMessage=RTException.INVALID_EXPRESSION;
						throw new RTException(errMessage);
					} else {
						op=(Operation)subExp;
					}
					isOperation = false;//следующим должeн быть терм
				}
			}
			evaluatedValue= res;
			isEvaluated = true;
		}
		return evaluatedValue;
	}
	
	public int intValue() throws RTException {
		return eval();
	}
	public String stringValue() throws RTException {
		return Integer.toString(this.intValue());
	}
}
/**Ячейка с ошибкой.*/
class ErrorCell extends Cell {
	public static final char FIRST_SIMBOL = '#';
	private String value;
	private String errMessage;
	public ErrorCell(String value, String err) {
		this.value = value;
		this.errMessage = err;
	}
	/**Возвращает текст ошибки*/
	public String stringValue() throws RTException {
		throw new RTException(errMessage);
	}
	/**Возвращает текст ошибки*/
	public int intValue() throws RTException {
		throw new RTException(errMessage);
	}
	/**Возвращает исходную строку*/
	public String stringOriginalValue() {
		return value;
	}
}
//*************Конец:Виды ячеек***********************************

/**Класс, описывающий электронную таблицу из тестового задания
 * rowsNumber, colsNumber - количество строк и столбцов
 * table - таблица
 *	*/
class LittleExcel {
	/**Поскольку ссылки на ячейки состоят из одной латинской буквы и следующей за ней цифры,
	 * то значит кол-во строк не может быть больше 9, а кол-во столбцов не может быть больше 26*/
	private static final byte MAX_ROWS = 9;
	private static final byte MAX_COLUMNS = 26;
	
	private byte rowsNumber; 
	private byte colsNumber; 
	private Cell[][] table; 

	/**Программа получает описание таблицы с формулами из стандартного ввода, 
	 * вычисляет ее и печатает полученный результат в стандартный вывод. 
	 * Входные данные представлены таблицей, элементы строк которой разделены табуляциями. 
	 * Первая строка содержит пару чисел, разделенных табуляцией - высоту и ширину таблицы, 
	 * соответственно. Затем идут строки с ячейками таблицы, в грамматике, описанной отдельно.
	 */
	public void load(InputStream inStr) throws IOException, XoredTestException {
		BufferedReader rdr = new BufferedReader(new InputStreamReader(inStr));
		String str;
		
		//Первая строка с высотой и шириной таблицы
		if ((str = rdr.readLine())!= null) {
			StringTokenizer st = new StringTokenizer(str,"\t",false);
			
			//Пытаемся загрузить количество строк
			if (st.hasMoreTokens()) {
				try {
					rowsNumber = Byte.parseByte(st.nextToken());
					if (rowsNumber<=0 || rowsNumber>MAX_ROWS) {
						
						throw new LoadException(LoadException.ROWS_NUMBER_ERROR);
					}
				} catch (NumberFormatException e) {
					throw new LoadException(LoadException.ROWS_NUMBER_ERROR);
				}
				
			}
			else{
				throw new LoadException(LoadException.ROWS_NUMBER_NOT_FOUND);
			}
			//Пытаемся загрузить количество столбцов
			if (st.hasMoreTokens()) {
				try {
					colsNumber = Byte.parseByte(st.nextToken());
					if (colsNumber<=0 || colsNumber>MAX_COLUMNS) {
						throw new LoadException(LoadException.COLS_NUMBER_ERROR);
					}
				} catch (NumberFormatException e) {
					throw new LoadException(LoadException.COLS_NUMBER_ERROR);
				}
				
			}
			else {
				throw new LoadException(LoadException.COLS_NUMBER_NOT_FOUND);
			}
		}
		else {
			throw new LoadException(LoadException.ROWS_N_COLS_NUMBER_NOT_FOUND);
		}
		
		//Создаем таблицу
		table = new Cell[rowsNumber][colsNumber];
		
		//Заполняем ячейки
		int rowIndex = 0;
		while (((str = rdr.readLine()) != null) && (rowIndex<rowsNumber)) {
			int colIndex=0;
			byte tabsInSeq=0;//счетчик подряд идущих табуляций
			StringTokenizer st = new StringTokenizer(str,"\t",true);
			while (st.hasMoreTokens() && (colIndex<colsNumber)) {
				String currToken = st.nextToken();
				if (!currToken.equals("\t")) {
					table[rowIndex][colIndex] = generateCell(currToken);
					colIndex++;
					tabsInSeq=0;
				} else {
					tabsInSeq++;
					if (tabsInSeq>1) {//две подряд табуляции означают пустую ячейку
						table[rowIndex][colIndex] = generateCell("");
						colIndex++;
						
					}
				}
			}
			//Если в строке ввода больше нету данных, но не все столбцы прошли, то заполняем оставшиеся столбцы пустыми ячейками 
			while (colIndex<colsNumber) {
				table[rowIndex][colIndex] = generateCell("");
				colIndex++;
			}
			rowIndex++;
		}
		//Если больше нету строк ввода, но не все строки таблицы прошли, то заполняем оставшиеся строки пустыми ячейками 
		while (rowIndex<rowsNumber) {
			int colIndex=0;
			while (colIndex<colsNumber) {
				table[rowIndex][colIndex] = generateCell("");
				colIndex++;
			}
			rowIndex++;
		}
	}
	
	/**Генерирует ячейку исходя из поданной строки*/
	private Cell generateCell(String text) {
		Cell tmpCell;
		if (text.length()==0) {//Пустая ячейка
			tmpCell = new EmptyCell();
		} else {
			char frstSymb = text.charAt(0);
			if (frstSymb==TextCell.FIRST_SIMBOL) {//Текстовая ячейка
				tmpCell = new TextCell(text.substring(1));
			} else if (frstSymb==ExprCell.FIRST_SIMBOL) {//Ячейка с выражением
				tmpCell = new ExprCell(this,text.substring(1));
			} else {
				try {//Пытаемся сделать числовую ячейку и здесь можем поймать две ошибки
					tmpCell = new NumCell(Integer.parseInt(text));
				} catch (NumberFormatException e) {
					tmpCell = new ErrorCell(text,RTException.INVALID_EXPRESSION);
				} catch (RTException e) {
					tmpCell = new ErrorCell(text,e.getLocalizedMessage());
				}
			}
		}
		return tmpCell;
	}
	/**Вывод результата с обработкой внутренних ошибок
	 * */
	public void print(OutputStream outStr) throws IOException {
		BufferedWriter rtr = new BufferedWriter(new OutputStreamWriter(outStr));
		for (int i=0; i<rowsNumber;i++) {
			for (int j=0; j<colsNumber;j++) {
				String str;
				try {
					str =table[i][j].stringValue();
				} catch (RTException e) {
					str=ErrorCell.FIRST_SIMBOL+e.getLocalizedMessage();
				}
				rtr.write(str);
				if (j<colsNumber-1) rtr.write("\t");
			}
			rtr.newLine();
		}
		rtr.flush();
	}

	/**Возвращает ячейку по заданным координатам
	 * */
	public Cell getCell(byte rowIndex, byte colIndex) throws RTException {
		if (rowIndex<0 || rowIndex>=rowsNumber || colIndex<0 ||colIndex>=colsNumber) {
			throw new RTException(RTException.DEAD_LINK);
		}
		return table[rowIndex][colIndex];
	}
}

//***********Блок исключений**************************
/**Исключения приложения*/
class XoredTestException extends Exception{
	private static final long serialVersionUID = 7449144684575730962L;

	public XoredTestException(String message){
		super(message);
	}
}
/**Исключения приложения, возникшие при загрузке и обработке входных данных*/
class LoadException extends XoredTestException{
	private static final long serialVersionUID = 8241521256430426195L;
	
	public static final String ROWS_NUMBER_ERROR = "Количество строк не соответствует критериям";
	public static final String ROWS_NUMBER_NOT_FOUND = "Не найдено количество строк";
	public static final String COLS_NUMBER_ERROR = "Количество столбцов не соответствует критериям";
	public static final String COLS_NUMBER_NOT_FOUND = "Не найдено количество столбцов";
	public static final String ROWS_N_COLS_NUMBER_NOT_FOUND = "Не найдено количество строк и столбцов";
	
	public LoadException(String message){
		super(message);
	}
}

/**Исключения приложения, возникшие при вычислении выражений*/
class RTException extends XoredTestException{
	private static final long serialVersionUID = 7000285642750670127L;

	public static final String NEGATIVE_NUMBER = "ОТР_ЧСЛ";
	public static final String DEAD_LINK = "ОШ_ССЛК";
	public static final String OVERFLOW_ERROR = "ОШ_ПРПЛН";
	public static final String DIV0_ERROR = "ОШ_ДЛН0";
	public static final String TEXT_IN_EXPRESSION = "ТКСТ_ЯЧ";
	public static final String EMPTY_EXPRESSION = "ПУСТОЕ_ВРЖН";
	public static final String EMPTY_SUBEXPRESSION = "ПУСТОЕ_ПДВР";
	public static final String UNKNOWN_SUBEXPRESSION = "НЕИЗВ_ПДВР";
	public static final String INCOMPLETE_SUBEXPRESSION = "НЕОКОНЧ_ВРЖН";
	public static final String CYCLE = "ЦИКЛ";
	public static final String INVALID_EXPRESSION = "НЕКОРР_ВРЖН";
	
	public RTException(String message){
		super(message);
	}
}
//***********Конец: Блок исключений*********************
