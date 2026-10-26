/*
 * ASTList.java      
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;

public abstract class ASTList<T extends AST> extends AST {

    protected T head;
    protected ASTList<T> next;

    protected ASTList(T head, ASTList<T> next, SourcePosition pos) {
        super(pos);
        this.head = head;
        this.next = next;
        if (this.head != null) {
            this.head.parent = this;
        }
        if (this.next != null) {
            this.next.parent = this;
	}
    }

    public T getHead() {
        return head;
    }

    public ASTList<T> getNext() {
        return next;
    }

    public void setHead(T head) {
        this.head = head;
    }

    public void setNext(ASTList<T> next) {
        this.next = next;
    }

    public abstract boolean isEmpty();

    /* Example Usage (for debugging purposes):
     *
     * DeclList decls = new DeclList(...);
     *
     * decls.forEach(d -> System.out.println(d));
     *
     */

    public void forEach(Consumer<? super T> consumer) {
        ASTList<T> cur = this;
        while (!cur.isEmpty()) {
            consumer.accept(cur.getHead());
            cur = cur.getNext();
        }
    }

    public String toString() {
    	StringBuilder sb = new StringBuilder("[");
    	ASTList<T> cur = this;
    	while (!cur.isEmpty()) {
        	sb.append(cur.getHead());
        	cur = cur.getNext();
        	if (!cur.isEmpty()) sb.append(", ");
    	}
    	sb.append("]");
    	return sb.toString();
   }

   public int size() {
     	int count = 0;
     	ASTList<T> cur = this;
     	while (!cur.isEmpty()) {
           count++;
           cur = cur.getNext();
    	}
       return count;
   }

   public Stream<T> stream() {
    	List<T> list = new ArrayList<>();
    	forEach(list::add);
    	return list.stream();
   }

  public List<T> toList() {
    	List<T> result = new ArrayList<>();
    	forEach(result::add);
    	return result;
  }

  public void printDebug() {
    System.out.println(this.toString());
  }

}

