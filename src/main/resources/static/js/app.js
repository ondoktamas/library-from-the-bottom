// ---------------------------------------------------------------------
// Thin fetch() wrapper around the real REST API. No mock/local state -
// every read and write goes through the Spring Boot backend and the H2
// database behind it.
// ---------------------------------------------------------------------

const API_BASE = "/api";

async function handleResponse(response) {
  if (response.status === 204) return null;
  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }
  if (!response.ok) {
    throw new Error(data?.message ?? `Request failed with status ${response.status}`);
  }
  return data;
}

function buildQuery(params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) query.set(key, value);
  }
  const str = query.toString();
  return str ? `?${str}` : "";
}

const api = {
  async listBooks(filters = {}) {
    const response = await fetch(`${API_BASE}/books${buildQuery(filters)}`);
    return handleResponse(response);
  },
  async addBook(payload) {
    const response = await fetch(`${API_BASE}/books`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return handleResponse(response);
  },
  async updateBook(id, payload) {
    const response = await fetch(`${API_BASE}/books/${encodeURIComponent(id)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return handleResponse(response);
  },
  async deleteBook(id) {
    const response = await fetch(`${API_BASE}/books/${encodeURIComponent(id)}`, { method: "DELETE" });
    return handleResponse(response);
  },
  async borrowBook(bookId, borrowerId) {
    const response = await fetch(`${API_BASE}/books/${encodeURIComponent(bookId)}/borrow`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ borrowerId }),
    });
    return handleResponse(response);
  },

  async listBorrowers() {
    const response = await fetch(`${API_BASE}/borrowers`);
    return handleResponse(response);
  },
  async getBorrower(id) {
    const response = await fetch(`${API_BASE}/borrowers/${encodeURIComponent(id)}`);
    return handleResponse(response);
  },
  async addBorrower(payload) {
    const response = await fetch(`${API_BASE}/borrowers`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return handleResponse(response);
  },
  async updateBorrower(id, payload) {
    const response = await fetch(`${API_BASE}/borrowers/${encodeURIComponent(id)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    return handleResponse(response);
  },
  async deleteBorrower(id) {
    const response = await fetch(`${API_BASE}/borrowers/${encodeURIComponent(id)}`, { method: "DELETE" });
    return handleResponse(response);
  },

  async listLoans() {
    const response = await fetch(`${API_BASE}/loans`);
    return handleResponse(response);
  },
  async returnLoan(loanId) {
    const response = await fetch(`${API_BASE}/loans/${encodeURIComponent(loanId)}`, { method: "DELETE" });
    return handleResponse(response);
  },
};

// --- Toast ---

let toastTimer;
function showToast(message, isError = false) {
  const toast = document.getElementById("toast");
  toast.textContent = message;
  toast.classList.toggle("toast-error", isError);
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    toast.hidden = true;
  }, 3000);
}

function debounce(fn, delayMs) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delayMs);
  };
}

// --- Tabs ---

document.querySelectorAll(".nav-item").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(`panel-${btn.dataset.panel}`).classList.add("active");
  });
});

// --- Rendering helpers ---

function escapeHtml(str) {
  const div = document.createElement("div");
  div.textContent = str ?? "";
  return div.innerHTML;
}

function escapeAttr(str) {
  return escapeHtml(str).replace(/"/g, "&quot;");
}

async function renderBooks() {
  const filters = {
    id: document.getElementById("filter-id").value.trim(),
    title: document.getElementById("filter-title").value.trim(),
    author: document.getElementById("filter-author").value.trim(),
  };
  const list = await api.listBooks(filters);
  const tbody = document.getElementById("books-tbody");
  const empty = document.getElementById("books-empty");
  tbody.innerHTML = "";
  empty.hidden = list.length > 0;

  for (const book of list) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(book.title)}</td>
      <td>${escapeHtml(book.author)}</td>
      <td>${book.yearOfPublication}</td>
      <td>${escapeHtml(book.edition)}</td>
      <td><span class="badge ${book.available ? "badge-available" : "badge-borrowed"}">${book.available ? "Available" : "Borrowed"}</span></td>
      <td class="id-cell">${escapeHtml(book.id)}</td>
      <td class="actions-cell">
        <button class="btn btn-secondary btn-small" data-action="edit-book" data-id="${book.id}">Edit</button>
        <button class="btn btn-danger btn-small" data-action="delete-book" data-id="${book.id}">Delete</button>
      </td>
    `;
    tbody.appendChild(tr);
  }
}

async function renderBorrowers() {
  const list = await api.listBorrowers();
  const tbody = document.getElementById("borrowers-tbody");
  const empty = document.getElementById("borrowers-empty");
  tbody.innerHTML = "";
  empty.hidden = list.length > 0;

  for (const borrower of list) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(borrower.name)}</td>
      <td>${borrower.dateOfBirth}</td>
      <td>${escapeHtml(borrower.address)}</td>
      <td class="id-cell">${escapeHtml(borrower.id)}</td>
      <td class="actions-cell">
        <button class="btn btn-secondary btn-small" data-action="edit-borrower" data-id="${borrower.id}">Edit</button>
        <button class="btn btn-danger btn-small" data-action="delete-borrower" data-id="${borrower.id}">Delete</button>
      </td>
    `;
    tbody.appendChild(tr);
  }
}

async function renderLoans() {
  const [loanList, bookList, borrowerList] = await Promise.all([
    api.listLoans(),
    api.listBooks(),
    api.listBorrowers(),
  ]);

  const bookSelect = document.getElementById("pair-book-select");
  const borrowerSelect = document.getElementById("pair-borrower-select");
  const availableBooks = bookList.filter((b) => b.available);

  bookSelect.innerHTML = availableBooks.length
    ? availableBooks.map((b) => `<option value="${b.id}">${escapeHtml(b.title)}</option>`).join("")
    : '<option value="">No available books</option>';
  borrowerSelect.innerHTML = borrowerList.length
    ? borrowerList.map((b) => `<option value="${b.id}">${escapeHtml(b.name)}</option>`).join("")
    : '<option value="">No borrowers</option>';

  document.getElementById("pair-btn").disabled = !availableBooks.length || !borrowerList.length;

  const tbody = document.getElementById("loans-tbody");
  const empty = document.getElementById("loans-empty");
  tbody.innerHTML = "";
  empty.hidden = loanList.length > 0;

  for (const loan of loanList) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${escapeHtml(loan.bookTitle)}</td>
      <td>${escapeHtml(loan.borrowerName)}</td>
      <td>${new Date(loan.borrowedAt).toLocaleString()}</td>
      <td class="actions-cell">
        <button class="btn btn-secondary btn-small" data-action="unpair" data-id="${loan.id}">Return</button>
      </td>
    `;
    tbody.appendChild(tr);
  }
}

async function refreshAll() {
  await Promise.all([renderBooks(), renderBorrowers(), renderLoans()]);
}

// --- Filter bar ---

const debouncedRenderBooks = debounce(renderBooks, 300);
["filter-id", "filter-title", "filter-author"].forEach((id) => {
  document.getElementById(id).addEventListener("input", debouncedRenderBooks);
});
document.getElementById("clear-filters-btn").addEventListener("click", () => {
  ["filter-id", "filter-title", "filter-author"].forEach((id) => {
    document.getElementById(id).value = "";
  });
  renderBooks();
});

// --- Modal handling ---

const overlay = document.getElementById("modal-overlay");
const modalTitle = document.getElementById("modal-title");
const modalFields = document.getElementById("modal-fields");
const modalForm = document.getElementById("modal-form");

let modalContext = null; // { type: 'book' | 'borrower', id: string | null }

function openBookModal(book = null) {
  modalContext = { type: "book", id: book?.id ?? null };
  modalTitle.textContent = book ? "Edit Book" : "Add Book";
  modalFields.innerHTML = `
    <div class="field"><label>Title</label><input name="title" required value="${book ? escapeAttr(book.title) : ""}"></div>
    <div class="field"><label>Author</label><input name="author" required value="${book ? escapeAttr(book.author) : ""}"></div>
    <div class="field"><label>Year of publication</label><input name="yearOfPublication" type="number" min="1" required value="${book ? book.yearOfPublication : ""}"></div>
    <div class="field"><label>Edition</label><input name="edition" required value="${book ? escapeAttr(book.edition) : ""}"></div>
    ${book ? `<div class="field"><label>ID (fixed, not affected by edits)</label><input value="${escapeAttr(book.id)}" readonly></div>` : ""}
  `;
  overlay.hidden = false;
}

function openBorrowerModal(borrower = null) {
  modalContext = { type: "borrower", id: borrower?.id ?? null };
  modalTitle.textContent = borrower ? "Edit Borrower" : "Add Borrower";
  modalFields.innerHTML = `
    <div class="field"><label>Name</label><input name="name" required pattern="[A-Za-z ]+" title="Letters and spaces only" value="${borrower ? escapeAttr(borrower.name) : ""}"></div>
    <div class="field"><label>Date of birth</label><input name="dateOfBirth" type="date" required value="${borrower ? borrower.dateOfBirth : ""}"></div>
    <div class="field"><label>Address</label><input name="address" required value="${borrower ? escapeAttr(borrower.address) : ""}"></div>
    ${borrower ? `<div class="field"><label>ID (fixed, not affected by edits)</label><input value="${escapeAttr(borrower.id)}" readonly></div>` : ""}
  `;
  overlay.hidden = false;
}

function closeModal() {
  overlay.hidden = true;
  modalContext = null;
  modalForm.reset();
}

document.getElementById("add-book-btn").addEventListener("click", () => openBookModal());
document.getElementById("add-borrower-btn").addEventListener("click", () => openBorrowerModal());
document.getElementById("modal-cancel").addEventListener("click", closeModal);
overlay.addEventListener("click", (e) => {
  if (e.target === overlay) closeModal();
});

modalForm.addEventListener("submit", async (e) => {
  e.preventDefault();
  const formData = new FormData(modalForm);

  try {
    if (modalContext.type === "book") {
      const payload = {
        title: formData.get("title"),
        author: formData.get("author"),
        yearOfPublication: Number(formData.get("yearOfPublication")),
        edition: formData.get("edition"),
      };
      if (modalContext.id) {
        await api.updateBook(modalContext.id, payload);
        showToast("Book updated");
      } else {
        await api.addBook(payload);
        showToast("Book added");
      }
    } else if (modalContext.type === "borrower") {
      const payload = {
        name: formData.get("name"),
        dateOfBirth: formData.get("dateOfBirth"),
        address: formData.get("address"),
      };
      if (modalContext.id) {
        await api.updateBorrower(modalContext.id, payload);
        showToast("Borrower updated");
      } else {
        await api.addBorrower(payload);
        showToast("Borrower added");
      }
    }
    closeModal();
    await refreshAll();
  } catch (error) {
    showToast(error.message, true);
  }
});

// --- Table action delegation (edit / delete / unpair) ---

document.addEventListener("click", async (e) => {
  const btn = e.target.closest("button[data-action]");
  if (!btn) return;
  const { action, id } = btn.dataset;

  try {
    if (action === "edit-book") {
      const [book] = await api.listBooks({ id });
      openBookModal(book);
    } else if (action === "delete-book") {
      if (confirm("Delete this book? This will also remove its active loan, if any.")) {
        await api.deleteBook(id);
        showToast("Book deleted");
        await refreshAll();
      }
    } else if (action === "edit-borrower") {
      const borrower = await api.getBorrower(id);
      openBorrowerModal(borrower);
    } else if (action === "delete-borrower") {
      if (confirm("Delete this borrower? This will also remove their active loan, if any.")) {
        await api.deleteBorrower(id);
        showToast("Borrower deleted");
        await refreshAll();
      }
    } else if (action === "unpair") {
      await api.returnLoan(id);
      showToast("Book returned");
      await refreshAll();
    }
  } catch (error) {
    showToast(error.message, true);
  }
});

document.getElementById("pair-btn").addEventListener("click", async () => {
  const bookId = document.getElementById("pair-book-select").value;
  const borrowerId = document.getElementById("pair-borrower-select").value;
  if (!bookId || !borrowerId) return;
  try {
    await api.borrowBook(bookId, borrowerId);
    showToast("Book paired to borrower");
    await refreshAll();
  } catch (error) {
    showToast(error.message, true);
  }
});

// --- Init ---

refreshAll();
